package com.personal.rebooked.homepage.sections;

import com.personal.rebooked.book.BookChangedEvent;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.homepage.HomepageBooks;
import com.personal.rebooked.homepage.HomepageCache;
import com.personal.rebooked.utils.Constants.BookStatus;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class NewArrivalsSectionTest {
    private final HomepageCache cache = mock(HomepageCache.class);
    private final NewArrivalsSection section = new NewArrivalsSection(cache, mock(HomepageBooks.class));

    private static BookChangedEvent event(BookChangedEvent.Kind kind, BookStatus now, BookStatus before) {
        Book b = new Book();
        b.setStatus(now);
        return new BookChangedEvent(b, kind, before);
    }

    @Test
    void evictsWhenABookEntersOrLeavesActive() {
        section.onBookChanged(event(BookChangedEvent.Kind.CREATED, BookStatus.ACTIVE, null));
        section.onBookChanged(event(BookChangedEvent.Kind.STATUS_CHANGED, BookStatus.SOLD, BookStatus.ACTIVE));
        section.onBookChanged(event(BookChangedEvent.Kind.STATUS_CHANGED, BookStatus.ACTIVE, BookStatus.DRAFT));
        section.onBookChanged(event(BookChangedEvent.Kind.DELETED, BookStatus.ACTIVE, BookStatus.ACTIVE));
        verify(cache, times(4)).evict(NewArrivalsSection.KEY);
    }

    @Test
    void ignoresChangesThatDontTouchActive() {
        section.onBookChanged(event(BookChangedEvent.Kind.CREATED, BookStatus.DRAFT, null));
        section.onBookChanged(event(BookChangedEvent.Kind.UPDATED, BookStatus.ACTIVE, BookStatus.ACTIVE));
        section.onBookChanged(event(BookChangedEvent.Kind.STATUS_CHANGED, BookStatus.SOLD, BookStatus.INACTIVE));
        section.onBookChanged(event(BookChangedEvent.Kind.DELETED, BookStatus.DRAFT, BookStatus.DRAFT));
        verifyNoInteractions(cache);
    }
}
