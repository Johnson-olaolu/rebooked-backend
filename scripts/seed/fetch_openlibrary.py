#!/usr/bin/env python3
"""
Builds seed/openlibrary-books.json.gz, the book fixture the demo seeder (seed.demo=true) reads.

Run it only to regenerate the fixture; the output is committed so seeding works offline:
    python3 scripts/seed/fetch_openlibrary.py

Queries the Open Library search API one subject at a time (about one request a second) and keeps works that
have a cover, a title and an author. Each subject maps to one of the categories seeded from Constants.categoryList;
up to two more categories come from the work's other subjects.
"""
import gzip
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://openlibrary.org/search.json"
FIELDS = "key,title,author_name,author_key,cover_i,isbn,first_publish_year,subject,number_of_pages_median"
USER_AGENT = "rebooked-seed-fetcher/1.0"
PAGE_SIZE = 100
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "seed", "openlibrary-books.json.gz")

# (Open Library subject query, category name, pages of 100), in claim order: a work keeps the category of the first
# query that returns it. Nigerian and African literature come first and are weighted up.
QUERIES = [
    ('subject_key:african_literature', "Contemporary Fiction", 3),
    ('subject:"nigerian fiction"', "Contemporary Fiction", 2),
    ('subject_key:nigeria language:eng', "History", 2),
    ('subject_key:mystery language:eng', "Mystery & Thriller", 2),
    ('subject_key:thrillers language:eng', "Mystery & Thriller", 1),
    ('subject_key:science_fiction language:eng', "Science Fiction", 2),
    ('subject_key:fantasy language:eng', "Fantasy", 2),
    ('subject_key:romance language:eng', "Romance", 2),
    ('subject_key:historical_fiction language:eng', "Historical Fiction", 2),
    ('subject_key:horror language:eng', "Horror", 1),
    ('subject_key:self-help language:eng', "Self-Help", 2),
    ('subject_key:business language:eng', "Business & Economics", 1),
    ('subject_key:economics language:eng', "Business & Economics", 1),
    ('subject_key:travel language:eng', "Travel", 1),
    ('subject_key:true_crime language:eng', "True Crime", 1),
    ('subject_key:philosophy language:eng', "Philosophy", 1),
    ('subject_key:textbooks language:eng', "Textbooks", 2),
    ('subject_key:reference language:eng', "Reference Books", 1),
    ('subject_key:study_guides language:eng', "Study Guides", 1),
    ('subject_key:essays language:eng', "Essays & Academic Papers", 1),
    ('subject_key:picture_books language:eng', "Picture Books", 1),
    ('subject_key:juvenile_fiction language:eng', "Middle Grade Fiction", 3),
    ('subject_key:young_adult_fiction subject_key:fantasy language:eng', "Young Adult Fantasy", 1),
    ('subject:"young adult fiction" language:eng', "Young Adult Contemporary", 2),
    ('subject_key:cooking language:eng', "Cookbooks & Food", 1),
    ('subject_key:gardening language:eng', "Gardening", 1),
    ('subject_key:handicraft language:eng', "Crafts & DIY", 1),
    ('subject_key:health language:eng', "Health & Fitness", 1),
    ('subject_key:sports language:eng', "Sports & Outdoors", 1),
    ('subject_key:photography language:eng', "Photography", 1),
    ('subject_key:music language:eng', "Music & Performing Arts", 1),
    ('subject_key:motion_pictures language:eng', "Film & TV", 1),
    ('subject_key:spirituality language:eng', "Spiritual Growth", 1),
    ('subject_key:bible language:eng', "Religious Texts", 1),
    ('subject_key:meditation language:eng', "Meditation & Mindfulness", 1),
    ('subject_key:poetry language:eng', "Poetry", 2),
    ('subject_key:comic_books_strips_etc language:eng', "Comics & Graphic Novels", 2),
    ('subject_key:short_stories language:eng', "Short Story Collections", 1),
    # Broad subjects last: specific ones above claim their works first
    ('subject_key:classic_literature language:eng', "Classic Literature", 3),
    ('subject_key:fiction language:eng', "Contemporary Fiction", 3),
    ('subject_key:biography language:eng', "Biographies & Memoirs", 2),
    ('subject_key:science language:eng', "Science & Nature", 1),
    ('subject_key:nature language:eng', "Science & Nature", 1),
    ('subject_key:history language:eng', "History", 2),
    ('subject_key:art language:eng', "Art & Design", 1),
]

# Secondary categories: a work subject containing the keyword (lower case) adds the category.
KEYWORDS = [
    ("science fiction", "Science Fiction"), ("fantasy", "Fantasy"), ("mystery", "Mystery & Thriller"),
    ("thriller", "Mystery & Thriller"), ("detective", "Mystery & Thriller"), ("romance", "Romance"),
    ("love stories", "Romance"), ("historical fiction", "Historical Fiction"), ("horror", "Horror"),
    ("biography", "Biographies & Memoirs"), ("memoir", "Biographies & Memoirs"), ("self-help", "Self-Help"),
    ("economics", "Business & Economics"), ("business", "Business & Economics"), ("history", "History"),
    ("travel", "Travel"), ("true crime", "True Crime"), ("philosophy", "Philosophy"), ("poetry", "Poetry"),
    ("short stories", "Short Story Collections"), ("comic", "Comics & Graphic Novels"),
    ("graphic novel", "Comics & Graphic Novels"), ("cooking", "Cookbooks & Food"), ("gardening", "Gardening"),
    ("health", "Health & Fitness"), ("sports", "Sports & Outdoors"), ("photography", "Photography"),
    ("music", "Music & Performing Arts"), ("drama", "Music & Performing Arts"), ("spiritual", "Spiritual Growth"),
    ("meditation", "Meditation & Mindfulness"), ("classic", "Classic Literature"), ("textbook", "Textbooks"),
    ("young adult", "Young Adult Contemporary"), ("juvenile fiction", "Middle Grade Fiction"),
    ("picture books", "Picture Books"), ("essays", "Essays & Academic Papers"),
]

MAX_CATEGORIES = 3
MAX_SUBJECTS = 5


def fetch(query, page):
    params = urllib.parse.urlencode({"q": query, "fields": FIELDS, "limit": PAGE_SIZE, "page": page})
    request = urllib.request.Request(f"{API}?{params}", headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response).get("docs", [])
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
            wait = 5 * (attempt + 1)
            print(f"  retry in {wait}s after {e}", file=sys.stderr)
            time.sleep(wait)
    print(f"  giving up on {query} page {page}", file=sys.stderr)
    return []


def isbn13(isbns):
    for i in isbns or []:
        digits = "".join(c for c in i if c.isdigit())
        if len(digits) == 13 and digits.startswith(("978", "979")):
            return digits
    return None


def categories_for(primary, subjects):
    categories = [primary]
    lowered = [s.lower() for s in subjects or []]
    for keyword, category in KEYWORDS:
        if len(categories) >= MAX_CATEGORIES:
            break
        if category not in categories and any(keyword in s for s in lowered):
            categories.append(category)
    return categories


def short_subjects(subjects):
    picked = []
    for s in subjects or []:
        if len(picked) >= MAX_SUBJECTS:
            break
        # Skip machine tags ("nyt:...", "series:...") and very long headings
        if ":" in s or len(s) > 40 or s.lower() in (p.lower() for p in picked):
            continue
        picked.append(s)
    return picked


def main():
    works = {}
    for query, category, pages in QUERIES:
        for page in range(1, pages + 1):
            docs = fetch(query, page)
            kept = 0
            for d in docs:
                key = d.get("key")
                names = d.get("author_name") or []
                if not key or key in works or not d.get("cover_i") or not d.get("title") or not names:
                    continue
                keys = d.get("author_key") or []
                works[key] = {
                    "workKey": key,
                    "title": d["title"].strip(),
                    "authors": [{"name": n.strip(), "olKey": keys[i] if i < len(keys) else None}
                                for i, n in enumerate(names[:3])],
                    "coverId": d["cover_i"],
                    "isbn": isbn13(d.get("isbn")),
                    "year": d.get("first_publish_year"),
                    "pages": d.get("number_of_pages_median"),
                    "categories": categories_for(category, d.get("subject")),
                    "subjects": short_subjects(d.get("subject")),
                }
                kept += 1
            print(f"{query} p{page}: {len(docs)} docs, {kept} new, {len(works)} total")
            time.sleep(1)
            if len(docs) < PAGE_SIZE:
                break

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wt", encoding="utf-8") as f:
        json.dump(list(works.values()), f, ensure_ascii=False, separators=(",", ":"))
    print(f"Wrote {len(works)} works to {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
