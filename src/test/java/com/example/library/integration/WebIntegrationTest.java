package com.example.library.integration;

import com.example.library.model.Book;
import com.example.library.model.Member;
import com.example.library.repository.BookRepository;
import com.example.library.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security and page-rendering tests: every page must render without template errors.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Integration - login, security and pages")
class WebIntegrationTest {

    private static final String ADMIN = "admin@libratrack.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private BookRepository bookRepository;
    @Autowired private MemberRepository memberRepository;

    // ------------------------------------------------------------------ security

    @Test
    @DisplayName("Protected pages redirect anonymous users to the login page")
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Login page is public")
    void loginPageRenders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Welcome Back")));
    }

    @Test
    @DisplayName("Demo administrator can sign in")
    void demoAdminCanLogIn() throws Exception {
        mockMvc.perform(formLogin("/login").user("email", ADMIN).password("password", "admin123"))
                .andExpect(authenticated().withUsername(ADMIN))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @DisplayName("Wrong password is rejected")
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(formLogin("/login").user("email", ADMIN).password("password", "wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    // ------------------------------------------------------------------ pages render

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    @DisplayName("Dashboard renders live statistics")
    void dashboardRenders() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Total Books")))
                .andExpect(content().string(containsString("Book Issue Trend")));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    @DisplayName("Every main page renders")
    void mainPagesRender() throws Exception {
        String[] pages = {"/books", "/books?status=LOW_STOCK&q=a", "/books/new", "/members", "/members/new",
                "/circulation", "/circulation?tab=return", "/transactions", "/transactions?status=OVERDUE",
                "/reports", "/settings"};
        for (String page : pages) {
            mockMvc.perform(get(page)).andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    @DisplayName("Book and member detail and edit pages render")
    void detailPagesRender() throws Exception {
        Book book = bookRepository.findAll().get(0);
        Member member = memberRepository.findAll().get(0);
        mockMvc.perform(get("/books/" + book.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Borrowing history")));
        mockMvc.perform(get("/books/" + book.getId() + "/edit")).andExpect(status().isOk());
        mockMvc.perform(get("/members/" + member.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Borrowing history")));
        mockMvc.perform(get("/members/" + member.getId() + "/edit")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    @DisplayName("Unknown records show the not-found page")
    void unknownBookShowsNotFound() throws Exception {
        mockMvc.perform(get("/books/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("not found")));
    }

    // ------------------------------------------------------------------ forms

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    @DisplayName("Adding a book through the form redirects to its detail page")
    void addBookThroughForm() throws Exception {
        mockMvc.perform(post("/books").with(csrf())
                        .param("bookCode", "")
                        .param("title", "Web Form Book")
                        .param("author", "Form Author")
                        .param("isbn", "9784444444444")
                        .param("category", "Programming")
                        .param("publisher", "Form Press")
                        .param("totalQuantity", "3")
                        .param("availableQuantity", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/books/*"))
                .andExpect(flash().attributeExists("toastSuccess"));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    @DisplayName("Invalid book form is shown again with the error")
    void invalidBookFormShowsErrors() throws Exception {
        mockMvc.perform(post("/books").with(csrf())
                        .param("title", "")
                        .param("author", "Someone")
                        .param("isbn", "9784444444445")
                        .param("category", "Programming")
                        .param("totalQuantity", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Title cannot be empty")));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    @DisplayName("Issuing a book through the circulation form works")
    void issueBookThroughForm() throws Exception {
        Book book = bookRepository.findByAvailableQuantityGreaterThanOrderByTitleAsc(0).get(0);
        Member member = memberRepository.findByMemberCodeIgnoreCase("STU112").orElseThrow();
        LocalDate today = LocalDate.now();
        mockMvc.perform(post("/circulation/issue").with(csrf())
                        .param("memberId", member.getId().toString())
                        .param("bookId", book.getId().toString())
                        .param("issueDate", today.toString())
                        .param("dueDate", today.plusDays(14).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/circulation?tab=issue"))
                .andExpect(flash().attributeExists("toastSuccess"));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    @DisplayName("Issuing to a member at the limit shows the rule on the form")
    void issueBeyondLimitShowsError() throws Exception {
        Member ananya = memberRepository.findByMemberCodeIgnoreCase("STU101").orElseThrow();
        Book book = bookRepository.findByIsbn("9780132350884").orElseThrow();
        LocalDate today = LocalDate.now();
        mockMvc.perform(post("/circulation/issue").with(csrf())
                        .param("memberId", ananya.getId().toString())
                        .param("bookId", book.getId().toString())
                        .param("issueDate", today.toString())
                        .param("dueDate", today.plusDays(14).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cannot borrow more than 3 books")));
    }
}
