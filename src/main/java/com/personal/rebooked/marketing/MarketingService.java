package com.personal.rebooked.marketing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.personal.rebooked.book.BookService;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.service.AiService;
import com.personal.rebooked.utils.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.personal.rebooked.marketing.dto.AddToMailingListDTO;
import com.personal.rebooked.service.BrevoService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class MarketingService {

    private final BrevoService brevoService;

    private final BookService bookService;

    private final AiService aiService;

    @Value("${brevo.mailing-list.rebooked-monthly-id}")
    private Integer brevoListId;

//    public void createMailingList() {
//        // Logic for creating a mailing list using Brevo
//    }

    public Object addToMailingList(AddToMailingListDTO dto) {
        String email = dto.email();
        List<Integer> listIds = new ArrayList<>();
        listIds.add(brevoListId);
        return brevoService.addToMailingList(email, listIds);
    }

    //test ai service
    public List<String> testAIService(String prompt) throws Exception {
        Gson gson = new Gson();
        ObjectMapper objectMapper = new ObjectMapper();
        List<Book> books = bookService.findAll(Constants.BookStatus.ACTIVE);
//       // pick only 100 books
        books = books.subList(0, Math.min(20, books.size()));
        List<Map<String, String>> bookList = new ArrayList<>();
        for (Book book : books) {
            Map<String, String> bookMap = new HashMap<>();
            bookMap.put("id", book.getId());
            bookMap.put("title", book.getTitle());
            bookMap.put("author", book.authorNames());
            bookMap.put("description", book.getDescription());
            bookMap.put("viewCount", String.valueOf(book.getViewCount()));
//            bookMap.put("tags", book.getTags().stream().map(t -> t.getName()).reduce((a, b) -> a + ", " + b).orElse(""));
            bookMap.put("categories", book.getCategories().stream().map(c -> c.getName()).reduce((a, b) -> a + ", " + b).orElse(""));
            bookList.add(bookMap);
        }
//        String booksJson = gson.toJson(books);
        String booksJson = objectMapper.writeValueAsString(bookList);
        String response =  aiService.suggestBooks(booksJson);
        List<String> responseList = gson.fromJson(response, List.class);
        return responseList;
    }
}
