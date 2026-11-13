package com.example.library.controller;

import com.example.library.dto.IssueBookRequest;
import com.example.library.dto.ReturnBookResponse;
import com.example.library.exception.BusinessException;
import com.example.library.model.Loan;
import com.example.library.service.BookService;
import com.example.library.service.CirculationService;
import com.example.library.service.FineService;
import com.example.library.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Circulation desk with two tabs: Issue Book and Return Book.
 */
@Controller
@RequestMapping("/circulation")
public class CirculationController {

    private final CirculationService circulationService;
    private final MemberService memberService;
    private final BookService bookService;

    public CirculationController(CirculationService circulationService,
                                 MemberService memberService,
                                 BookService bookService) {
        this.circulationService = circulationService;
        this.memberService = memberService;
        this.bookService = bookService;
    }

    @GetMapping
    public String desk(@RequestParam(defaultValue = "issue") String tab,
                       @RequestParam(defaultValue = "") String q,
                       @RequestParam(required = false) Long memberId,
                       @RequestParam(required = false) Long bookId,
                       Model model) {
        if (!model.containsAttribute("issueRequest")) {
            LocalDate today = circulationService.today();
            model.addAttribute("issueRequest",
                    new IssueBookRequest(memberId, bookId, today, circulationService.defaultDueDate(today)));
        }
        return showDesk(model, "return".equalsIgnoreCase(tab) ? "return" : "issue", q);
    }

    @PostMapping("/issue")
    public String issue(@Valid @ModelAttribute("issueRequest") IssueBookRequest request, BindingResult result,
                        Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return showDesk(model, "issue", "");
        }
        try {
            Loan loan = circulationService.issueBook(request);
            redirect.addFlashAttribute(WebSupport.TOAST_SUCCESS, "'" + loan.getBook().getTitle() + "' issued to "
                    + loan.getMember().getFullName() + " - due on " + loan.getDueDate());
            return "redirect:/circulation?tab=issue";
        } catch (BusinessException ex) {
            WebSupport.reject(result, ex);
            return showDesk(model, "issue", "");
        }
    }

    @PostMapping("/return/{loanId}")
    public String returnBook(@PathVariable Long loanId,
                             @RequestParam(defaultValue = "") String q,
                             RedirectAttributes redirect) {
        try {
            ReturnBookResponse response = circulationService.returnBook(loanId);
            redirect.addFlashAttribute("returnResult", response);
            String fineText = response.hasFine()
                    ? " Fine: ₹" + response.fineAmount().toPlainString() + " (" + response.overdueDays() + " days late)."
                    : " No fine.";
            redirect.addFlashAttribute(WebSupport.TOAST_SUCCESS, "'" + response.bookTitle() + "' returned." + fineText);
        } catch (BusinessException ex) {
            redirect.addFlashAttribute(WebSupport.TOAST_ERROR, ex.getMessage());
        }
        String query = q.isBlank() ? "" : "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
        return "redirect:/circulation?tab=return" + query;
    }

    private String showDesk(Model model, String tab, String query) {
        model.addAttribute("activePage", "circulation");
        model.addAttribute("tab", tab);
        boolean returning = "return".equals(tab);
        model.addAttribute("pageTitle", returning ? "Return Book" : "Issue Book");
        model.addAttribute("pageSubtitle", returning ? "Check in borrowed books and collect fines" : "Issue a book to a registered member");
        model.addAttribute("q", query);
        model.addAttribute("members", memberService.findAllMembers());
        model.addAttribute("memberLoans", memberService.activeLoansPerMember());
        model.addAttribute("books", bookService.findAvailableBooks());
        model.addAttribute("activeLoans", circulationService.findActiveLoans(query));
        model.addAttribute("maxLoans", CirculationService.MAX_ACTIVE_LOANS);
        model.addAttribute("loanDays", CirculationService.DEFAULT_LOAN_PERIOD_DAYS);
        model.addAttribute("finePerDay", FineService.FINE_PER_DAY);
        model.addAttribute("today", circulationService.today());
        return "circulation/index";
    }
}
