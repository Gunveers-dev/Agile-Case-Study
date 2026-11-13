package com.example.library.controller;

import com.example.library.exception.BusinessException;
import com.example.library.model.Loan;
import com.example.library.model.Member;
import com.example.library.service.CirculationService;
import com.example.library.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

/**
 * Member pages: list/search, register, edit, profile with borrowing history, delete.
 */
@Controller
@RequestMapping("/members")
public class MemberController {

    private static final int PAGE_SIZE = 10;

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @InitBinder("member")
    void restrictBinding(WebDataBinder binder) {
        binder.setDisallowedFields("id", "registrationDate", "loans*");
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String department,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("activePage", "members");
        model.addAttribute("members", memberService.findMembers(q, department, page, PAGE_SIZE));
        model.addAttribute("departments", memberService.getDepartments());
        model.addAttribute("q", q);
        model.addAttribute("department", department);
        return "members/list";
    }

    @GetMapping("/new")
    public String newMember(Model model) {
        Member member = new Member();
        member.setMemberCode(memberService.nextMemberCode());
        return showForm(model, member, null);
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("member") Member member, BindingResult result,
                           Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return showForm(model, member, null);
        }
        try {
            Member saved = memberService.registerMember(member);
            redirect.addFlashAttribute(WebSupport.TOAST_SUCCESS,
                    saved.getFullName() + " was registered as " + saved.getMemberCode());
            return "redirect:/members/" + saved.getId();
        } catch (BusinessException ex) {
            WebSupport.reject(result, ex);
            return showForm(model, member, null);
        }
    }

    @GetMapping("/{id}")
    public String profile(@PathVariable Long id, Model model) {
        Member member = memberService.findMember(id);
        List<Loan> history = memberService.getBorrowingHistory(id);
        long active = history.stream().filter(Loan::isActive).count();
        BigDecimal finesPaid = history.stream()
                .map(Loan::getFineAmount)
                .filter(f -> f != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("activePage", "members");
        model.addAttribute("member", member);
        model.addAttribute("history", history);
        model.addAttribute("activeLoans", active);
        model.addAttribute("maxLoans", CirculationService.MAX_ACTIVE_LOANS);
        model.addAttribute("finesPaid", finesPaid);
        return "members/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        return showForm(model, memberService.findMember(id), id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("member") Member member, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return showForm(model, member, id);
        }
        try {
            Member saved = memberService.updateMember(id, member);
            redirect.addFlashAttribute(WebSupport.TOAST_SUCCESS, "Profile of " + saved.getFullName() + " was updated");
            return "redirect:/members/" + id;
        } catch (BusinessException ex) {
            WebSupport.reject(result, ex);
            return showForm(model, member, id);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            String name = memberService.findMember(id).getFullName();
            memberService.deleteMember(id);
            redirect.addFlashAttribute(WebSupport.TOAST_SUCCESS, name + " was removed from the members list");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute(WebSupport.TOAST_ERROR, ex.getMessage());
        }
        return "redirect:/members";
    }

    private String showForm(Model model, Member member, Long editingId) {
        model.addAttribute("activePage", "members");
        model.addAttribute("member", member);
        model.addAttribute("editing", editingId != null);
        model.addAttribute("memberId", editingId);
        model.addAttribute("pageTitle", editingId != null ? "Edit Member" : "Register Member");
        model.addAttribute("pageSubtitle", editingId != null ? "Update the member profile" : "Add a student or staff member");
        model.addAttribute("departments", memberService.getDepartments());
        return "members/form";
    }
}
