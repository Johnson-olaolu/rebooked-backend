package com.personal.rebooked.utils;

import com.personal.rebooked.category.dto.CreateCategoryDTO;
import com.personal.rebooked.user.dto.CreateUserDto;
import com.personal.rebooked.user.role.dto.CreateRoleDTO;

import java.util.Arrays;
import java.util.List;

public class Constants {

    public static List<CreateRoleDTO> userRoleList = List.of(
            new CreateRoleDTO("user", "USER"),
            new CreateRoleDTO("admin", "ADMIN"),
            new CreateRoleDTO("superAdmin", "SUPER ADMIN"));

    public static CreateUserDto superAdminDetails = new CreateUserDto(
            "super_admin@rebooked.com",
            "Super Admin",
            "Admin_123",
            "superAdmin",
            true,
            null);

    public static List<CreateCategoryDTO> categoryList = List.of(
            new CreateCategoryDTO("Classic Literature", "Classic literature books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/book-classic.svg", true),
            new CreateCategoryDTO("Contemporary Fiction", "Modern fiction books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/book.svg", true),
            new CreateCategoryDTO("Mystery & Thriller", "Mystery and thriller books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/magnifying-glass.svg", true),
            new CreateCategoryDTO("Science Fiction", "Sci-fi books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/rocket.svg", true),
            new CreateCategoryDTO("Fantasy", "Fantasy books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/wand-sparkles.svg", true),
            new CreateCategoryDTO("Romance", "Romance books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/heart.svg", true),
            new CreateCategoryDTO("Historical Fiction", "Historical fiction books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/landmark.svg", true),
            new CreateCategoryDTO("Horror", "Horror books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/ghost.svg", true),
            new CreateCategoryDTO("Biographies & Memoirs", "Biography books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/user-pen.svg", true),
            new CreateCategoryDTO("Self-Help", "Self-help books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/hand-holding-heart.svg", true),
            new CreateCategoryDTO("Business & Economics", "Business books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/briefcase.svg", true),
            new CreateCategoryDTO("Science & Nature", "Science books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/flask.svg", true),
            new CreateCategoryDTO("History", "History books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/monument.svg", true),
            new CreateCategoryDTO("Travel", "Travel books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/plane.svg", true),
            new CreateCategoryDTO("True Crime", "True crime books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/handcuffs.svg", true),
            new CreateCategoryDTO("Philosophy", "Philosophy books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/brain.svg", true),
            new CreateCategoryDTO("Textbooks", "Academic textbooks",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/graduation-cap.svg", true),
            new CreateCategoryDTO("Reference Books", "Reference materials",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/books.svg", true),
            new CreateCategoryDTO("Study Guides", "Study materials",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/memo-pad.svg", true),
            new CreateCategoryDTO("Essays & Academic Papers", "Academic papers",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/file-lines.svg", true),
            new CreateCategoryDTO("Picture Books", "Children's picture books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/images.svg", true),
            new CreateCategoryDTO("Middle Grade Fiction", "Middle grade books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/child.svg", true),
            new CreateCategoryDTO("Young Adult Fantasy", "YA fantasy books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/dragon.svg", true),
            new CreateCategoryDTO("Young Adult Contemporary", "YA contemporary books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/user-graduate.svg", true),
            new CreateCategoryDTO("Cookbooks & Food", "Cooking books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/utensils.svg", true),
            new CreateCategoryDTO("Gardening", "Gardening books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/seedling.svg", true),
            new CreateCategoryDTO("Crafts & DIY", "Craft books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/scissors.svg", true),
            new CreateCategoryDTO("Health & Fitness", "Health books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/dumbbell.svg", true),
            new CreateCategoryDTO("Sports & Outdoors", "Sports books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/baseball.svg", true),
            new CreateCategoryDTO("Photography", "Photography books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/camera.svg", true),
            new CreateCategoryDTO("Art & Design", "Art books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/palette.svg", true),
            new CreateCategoryDTO("Music & Performing Arts", "Music books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/music.svg", true),
            new CreateCategoryDTO("Film & TV", "Film and TV books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/film.svg", true),
            new CreateCategoryDTO("Spiritual Growth", "Spiritual books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/om.svg", true),
            new CreateCategoryDTO("Religious Texts", "Religious books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/place-of-worship.svg", true),
            new CreateCategoryDTO("Meditation & Mindfulness", "Meditation books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/peace.svg", true),
            new CreateCategoryDTO("Poetry", "Poetry books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/feather.svg", true),
            new CreateCategoryDTO("Comics & Graphic Novels", "Comic books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/masks-theater.svg", true),
            new CreateCategoryDTO("Short Story Collections", "Short story books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/bookmark.svg", true),
            new CreateCategoryDTO("Rare & Collectible Editions", "Rare books",
                    "https://site-assets.fontawesome.com/releases/v6.5.1/svgs/solid/gem.svg", true));

    public enum RegistrationType {
        GOOGLE,
        FACEBOOK,
        EMAIL
    }

    public enum BookStatus {
        ACTIVE,
        INACTIVE,
        SOLD
    }

    public enum TimeQuery {
        LAST_WEEK,
        LAST_TWO_WEEKS,
        LAST_MONTH,
        LAST_SIX_MONTHS,
        LAST_YEAR
    }

    public enum ChatType {
        REQUEST,
        BID,
        MESSAGE
    }

    public enum NotificationMetaType {
        NEW_MESSAGE,
        BOOK_STATUS_CHANGED
    }

    public record BookData(String title, String author, String description) {}

    public static final List<BookData> SAMPLE_BOOKS = Arrays.asList(
            // Classic Fiction
            new BookData("To Kill a Mockingbird", "Harper Lee", "A gripping tale of racial injustice and childhood in the American South"),
            new BookData("1984", "George Orwell", "A dystopian masterpiece exploring totalitarianism and surveillance"),
            new BookData("Pride and Prejudice", "Jane Austen", "A timeless romance and social commentary on 19th century England"),
            new BookData("The Great Gatsby", "F. Scott Fitzgerald", "The decline of the American Dream in the Jazz Age"),
            new BookData("Lord of the Flies", "William Golding", "A haunting exploration of human nature and civilization"),

            // Modern Fiction
            new BookData("The Handmaid's Tale", "Margaret Atwood", "A chilling vision of a totalitarian future"),
            new BookData("Beloved", "Toni Morrison", "A powerful story of slavery and its lasting trauma"),
            new BookData("The Road", "Cormac McCarthy", "A post-apocalyptic journey of survival and love"),
            new BookData("Life of Pi", "Yann Martel", "An extraordinary tale of survival and faith"),
            new BookData("The Kite Runner", "Khaled Hosseini", "A story of friendship, guilt, and redemption in Afghanistan"),

            // Science Fiction & Fantasy
            new BookData("Dune", "Frank Herbert", "An epic space opera of politics, religion, and ecology"),
            new BookData("The Hitchhiker's Guide to the Galaxy", "Douglas Adams", "A hilarious journey through space and time"),
            new BookData("Foundation", "Isaac Asimov", "The rise and fall of a galactic empire"),
            new BookData("The Lord of the Rings", "J.R.R. Tolkien", "The ultimate fantasy epic of good versus evil"),
            new BookData("Neuromancer", "William Gibson", "The cyberpunk novel that defined a genre"),

            // Non-Fiction
            new BookData("Sapiens", "Yuval Noah Harari", "A brief history of humankind and our rise to dominance"),
            new BookData("Educated", "Tara Westover", "A memoir of education and self-discovery"),
            new BookData("The Immortal Life of Henrietta Lacks", "Rebecca Skloot", "The story of cells that changed medicine"),
            new BookData("Thinking, Fast and Slow", "Daniel Kahneman", "How the mind makes decisions"),
            new BookData("In Cold Blood", "Truman Capote", "A groundbreaking work of narrative non-fiction"),

            // Mystery & Thriller
            new BookData("Gone Girl", "Gillian Flynn", "A psychological thriller about a marriage gone wrong"),
            new BookData("The Girl with the Dragon Tattoo", "Stieg Larsson", "A dark Swedish crime thriller"),
            new BookData("And Then There Were None", "Agatha Christie", "The queen of mystery's most famous work"),
            new BookData("The Silence of the Lambs", "Thomas Harris", "A chilling psychological thriller"),
            new BookData("Big Little Lies", "Liane Moriarty", "Secrets and lies in suburban Australia"),

            // Romance
            new BookData("Outlander", "Diana Gabaldon", "Time-traveling romance and adventure"),
            new BookData("The Notebook", "Nicholas Sparks", "A timeless love story"),
            new BookData("Me Before You", "Jojo Moyes", "A heart-wrenching tale of love and loss"),
            new BookData("The Time Traveler's Wife", "Audrey Niffenegger", "Love transcending time"),
            new BookData("It Ends with Us", "Colleen Hoover", "A powerful story of love and resilience"),

            // Literary Fiction
            new BookData("The Goldfinch", "Donna Tartt", "Art, loss, and the meaning of beauty"),
            new BookData("A Little Life", "Hanya Yanagihara", "A profound exploration of friendship and trauma"),
            new BookData("The Brief Wondrous Life of Oscar Wao", "Junot Díaz", "A multi-generational Dominican-American saga"),
            new BookData("Station Eleven", "Emily St. John Mandel", "Civilization after the collapse"),
            new BookData("The Seven Husbands of Evelyn Hugo", "Taylor Jenkins Reid", "The secrets of a Hollywood icon")
    );


    // Additional title variations to reach 500 books
    public static final List<String> TITLE_PREFIXES = Arrays.asList(
            "The Art of", "Secrets of", "Journey to", "Tales from", "The Last", "Beyond the",
            "Shadows of", "Dreams of", "The Hidden", "Whispers of", "The Lost", "Echoes of"
    );

    public static final List<String> TITLE_SUFFIXES = Arrays.asList(
            "Midnight", "Dawn", "Tomorrow", "Yesterday", "Forever", "Paradise", "Eternity",
            "Kingdom", "Empire", "Island", "Mountain", "Ocean", "Storm", "Fire", "Light"
    );

    public static final List<String> AUTHORS = Arrays.asList(
            // Classic Literature
            "William Shakespeare",
            "Jane Austen",
            "Charles Dickens",
            "Mark Twain",
            "Leo Tolstoy",
            "Fyodor Dostoevsky",
            "Virginia Woolf",
            "Ernest Hemingway",
            "F. Scott Fitzgerald",
            "George Orwell",
            "Harper Lee",
            "J.D. Salinger",
            "William Faulkner",
            "Emily Dickinson",
            "Walt Whitman",
            "Oscar Wilde",
            "Charlotte Brontë",
            "Emily Brontë",
            "Herman Melville",
            "Nathaniel Hawthorne",

            // Contemporary Fiction
            "Margaret Atwood",
            "Toni Morrison",
            "Salman Rushdie",
            "Isabel Allende",
            "Haruki Murakami",
            "Zadie Smith",
            "Chimamanda Ngozi Adichie",
            "Donna Tartt",
            "Jonathan Franzen",
            "Jennifer Egan",
            "Colson Whitehead",
            "Hanya Yanagihara",
            "Elena Ferrante",
            "Karl Ove Knausgård",
            "Jhumpa Lahiri",
            "Alice Munro",
            "Joyce Carol Oates",
            "Don DeLillo",
            "Paul Auster",
            "Kazuo Ishiguro",
            "Ian McEwan",
            "Julian Barnes",
            "Hilary Mantel",
            "Anne Enright",
            "Celeste Ng",

            // Science Fiction & Fantasy
            "J.R.R. Tolkien",
            "Ursula K. Le Guin",
            "Isaac Asimov",
            "Philip K. Dick",
            "Frank Herbert",
            "Ray Bradbury",
            "Arthur C. Clarke",
            "Robert A. Heinlein",
            "William Gibson",
            "Neil Gaiman",
            "Terry Pratchett",
            "George R.R. Martin",
            "Kim Stanley Robinson",
            "Octavia Butler",
            "Douglas Adams",

            // Mystery & Thriller
            "Agatha Christie",
            "Arthur Conan Doyle",
            "Raymond Chandler",
            "Gillian Flynn",
            "Tana French",
            "Louise Penny",
            "John le Carré",
            "Patricia Highsmith",
            "Michael Crichton",
            "Stieg Larsson",

            // Non-Fiction
            "Malcolm Gladwell",
            "Mary Roach",
            "Bill Bryson",
            "Rebecca Skloot",
            "Yuval Noah Harari",
            "Michelle Obama",
            "Ta-Nehisi Coates",
            "Jon Krakauer",
            "Atul Gawande",
            "Susan Orlean",

            // Romance & Popular Fiction
            "Nicholas Sparks",
            "Jodi Picoult",
            "Danielle Steel",
            "Nora Roberts",
            "LaVyrle Spencer",
            "Diana Gabaldon",
            "Colleen Hoover",
            "Emily Henry",
            "Christina Lauren",
            "Taylor Jenkins Reid",

            // International & Diverse Voices
            "Gabriel García Márquez",
            "Paulo Coelho",
            "Chinua Achebe",
            "Arundhati Roy",
            "Khaled Hosseini",
            "Yann Martel",
            "Amin Maalouf",
            "Orhan Pamuk",
            "José Saramago",
            "Italo Calvino"
    );
}
