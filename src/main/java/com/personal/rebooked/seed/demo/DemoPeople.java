package com.personal.rebooked.seed.demo;

import java.util.List;
import java.util.Random;

/** Names, places and words for the demo users, listings and ratings. State names match Constants.NIGERIAN_STATES. */
final class DemoPeople {
    private DemoPeople() {}

    static final List<String> FIRST_NAMES = List.of(
            "Adebayo", "Adaeze", "Chinedu", "Chiamaka", "Oluwaseun", "Funmilayo", "Emeka", "Ngozi", "Tunde", "Yetunde",
            "Ibrahim", "Aisha", "Musa", "Fatima", "Uche", "Amaka", "Segun", "Bisi", "Kelechi", "Ifeoma",
            "Babajide", "Folake", "Obinna", "Nneka", "Tobi", "Temitope", "Abdullahi", "Zainab", "Chukwuemeka", "Ebere",
            "Damilola", "Kemi", "Ikenna", "Chioma", "Femi", "Toyin", "Usman", "Hauwa", "Nnamdi", "Adanna",
            "Kayode", "Bukola", "Sani", "Halima", "Chidi", "Onyinye", "Gbenga", "Omolara", "Yusuf", "Maryam",
            "Ayodele", "Ronke", "Osagie", "Efosa", "Ese", "Osas", "Tamuno", "Ibiere", "Ekaette", "Ini",
            "David", "Grace", "Samuel", "Esther", "Daniel", "Blessing", "Michael", "Joy", "Victor", "Faith");

    static final List<String> LAST_NAMES = List.of(
            "Adeyemi", "Okafor", "Bello", "Okonkwo", "Adebayo", "Eze", "Abubakar", "Nwosu", "Ogunleye", "Ibrahim",
            "Oladipo", "Chukwu", "Mohammed", "Obi", "Afolabi", "Nwachukwu", "Balogun", "Okeke", "Yusuf", "Ogunbiyi",
            "Uzor", "Lawal", "Onyekachi", "Adewale", "Igwe", "Danjuma", "Akinola", "Ekwueme", "Olatunji", "Nnadi",
            "Osagie", "Edet", "Bassey", "Ikpe", "Aliyu", "Garba", "Ojo", "Ogundipe", "Anyanwu", "Ezeh",
            "Fashola", "Odunsi", "Okoro", "Babatunde", "Umeh", "Adeleke", "Idowu", "Etim", "Briggs", "Amadi");

    record City(String name, double lat, double lng) {}

    record State(String name, int weight, List<City> cities) {}

    /** Weighted towards where most book buyers are: Lagos about a third. */
    static final List<State> STATES = List.of(
            new State("Lagos", 35, List.of(new City("Ikeja", 6.6018, 3.3515), new City("Lekki", 6.4698, 3.5852),
                    new City("Yaba", 6.5095, 3.3711), new City("Surulere", 6.5000, 3.3500),
                    new City("Victoria Island", 6.4281, 3.4219), new City("Ikorodu", 6.6194, 3.5105),
                    new City("Ajah", 6.4667, 3.5667))),
            new State("Federal Capital Territory", 12, List.of(new City("Abuja", 9.0765, 7.3986),
                    new City("Gwarinpa", 9.1108, 7.4165), new City("Wuse", 9.0700, 7.4800), new City("Kubwa", 9.1544, 7.3226))),
            new State("Oyo", 8, List.of(new City("Ibadan", 7.3775, 3.9470), new City("Ogbomosho", 8.1335, 4.2407))),
            new State("Rivers", 7, List.of(new City("Port Harcourt", 4.8156, 7.0498), new City("Obio-Akpor", 4.8667, 7.0167))),
            new State("Ogun", 5, List.of(new City("Abeokuta", 7.1475, 3.3619), new City("Ota", 6.6804, 3.2356),
                    new City("Ijebu-Ode", 6.8194, 3.9173))),
            new State("Kano", 4, List.of(new City("Kano", 12.0022, 8.5920))),
            new State("Enugu", 4, List.of(new City("Enugu", 6.4584, 7.5464), new City("Nsukka", 6.8567, 7.3958))),
            new State("Kaduna", 3, List.of(new City("Kaduna", 10.5105, 7.4165), new City("Zaria", 11.0855, 7.7199))),
            new State("Edo", 3, List.of(new City("Benin City", 6.3350, 5.6037))),
            new State("Anambra", 3, List.of(new City("Awka", 6.2106, 7.0741), new City("Onitsha", 6.1413, 6.8029))),
            new State("Delta", 2, List.of(new City("Asaba", 6.1983, 6.7322), new City("Warri", 5.5167, 5.7500))),
            new State("Osun", 2, List.of(new City("Osogbo", 7.7827, 4.5418), new City("Ile-Ife", 7.4905, 4.5521))),
            new State("Kwara", 2, List.of(new City("Ilorin", 8.4966, 4.5421))),
            new State("Plateau", 2, List.of(new City("Jos", 9.8965, 8.8583))),
            new State("Akwa Ibom", 2, List.of(new City("Uyo", 5.0377, 7.9128))),
            new State("Cross River", 1, List.of(new City("Calabar", 4.9757, 8.3417))),
            new State("Imo", 2, List.of(new City("Owerri", 5.4840, 7.0351))),
            new State("Abia", 1, List.of(new City("Umuahia", 5.5250, 7.4922), new City("Aba", 5.1066, 7.3667))),
            new State("Ondo", 1, List.of(new City("Akure", 7.2571, 5.2058))),
            new State("Ekiti", 1, List.of(new City("Ado-Ekiti", 7.6211, 5.2214))));

    static final List<String> SHOP_SUFFIXES = List.of(
            "Books", "Bookshelf", "Book Corner", "Reads", "Book Nook", "Library", "Book Hub", "Pages", "Book Stall");

    static final List<String> SELLER_BIOS = List.of(
            "Clearing out my shelves. Everything has been read once and well kept.",
            "Avid reader and collector. Happy to meet at a public place or arrange delivery.",
            "Secondhand books at fair prices. Message me for bundles.",
            "University lecturer selling books I no longer need. Most are in great condition.",
            "I run a small bookstall and list my stock here. Pickup or delivery within the city.",
            "Book club host. When we finish a book, it finds a new home.",
            "Parent of three selling outgrown children's books and my own novels.",
            "Mostly fiction and African literature. Quick replies, honest descriptions.",
            "Student selling textbooks and study guides after exams.",
            "Lifelong reader making space for new books. Prices are negotiable.");

    static final List<String> CONDITION_NOTES_NEW = List.of(
            "Brand new, never opened.", "New copy, still in its shrink wrap.", "Unread gift copy.");
    static final List<String> CONDITION_NOTES_LIKE_NEW = List.of(
            "Read once, no marks or creases.", "Like new: clean pages and a tight spine.",
            "Excellent condition, kept on a shelf away from the sun.");
    static final List<String> CONDITION_NOTES_GOOD = List.of(
            "Some wear on the cover, pages clean.", "Good reading copy with a slightly creased spine.",
            "A name written inside the cover, otherwise clean.", "Light shelf wear, no missing pages.");
    static final List<String> CONDITION_NOTES_FAIR = List.of(
            "Well loved: worn cover and some highlighting.", "Fair condition, yellowed pages but complete.",
            "Spine is cracked but every page is there.");

    static final List<String> COMMENTS_GOOD = List.of(
            "Book arrived exactly as described. Very smooth transaction.",
            "Great seller, quick replies and the book was in perfect condition.",
            "Met up on time and the copy was even better than the photos.",
            "Lovely condition and fair price. Would buy from again.",
            "Delivery was fast and the book was well packaged.",
            "Honest description and friendly seller. Highly recommended.",
            "Exactly what I was looking for. Thank you!");
    static final List<String> COMMENTS_OK = List.of(
            "Book was fine, a little more worn than described.",
            "Took a while to arrange pickup but the book was okay.",
            "Decent copy for the price, slight delay in replying.");
    static final List<String> COMMENTS_BAD = List.of(
            "The book had more highlighting than the listing said.",
            "Seller was late to the meetup and the cover was damaged.",
            "Not as described, pages were missing from the back.");

    static <T> T pick(List<T> list, Random random) {
        return list.get(random.nextInt(list.size()));
    }

    static State pickState(Random random) {
        int total = STATES.stream().mapToInt(State::weight).sum();
        int r = random.nextInt(total);
        for (State s : STATES) {
            r -= s.weight();
            if (r < 0) return s;
        }
        return STATES.get(0);
    }

    static String comment(int stars, Random random) {
        return pick(stars >= 4 ? COMMENTS_GOOD : stars == 3 ? COMMENTS_OK : COMMENTS_BAD, random);
    }
}
