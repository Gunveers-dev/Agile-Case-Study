package com.example.library.service;

import com.example.library.exception.BusinessException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.model.Member;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberService - registration rules")
class MemberServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private LoanRepository loanRepository;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        memberService = new MemberService(memberRepository, loanRepository, clock);
    }

    @Test
    @DisplayName("Registers a member: tidies the ID and email and sets today's registration date")
    void registersMember() {
        Member input = new Member(" stu201 ", "Riya Sen", "Riya.Sen@College.EDU", "9876543210", "CSE");
        when(memberRepository.existsByMemberCodeIgnoreCase("STU201")).thenReturn(false);
        when(memberRepository.existsByEmailIgnoreCase("riya.sen@college.edu")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            member.setId(13L);
            return member;
        });

        Member saved = memberService.registerMember(input);

        assertEquals(13L, saved.getId());
        assertEquals("STU201", saved.getMemberCode());
        assertEquals("riya.sen@college.edu", saved.getEmail());
        assertEquals(TODAY, saved.getRegistrationDate());
        verify(memberRepository).save(input);
    }

    @Test
    @DisplayName("Rejects a member ID that is already registered")
    void rejectsDuplicateMemberId() {
        Member input = new Member("STU101", "Another Student", "another@college.edu", "9876543210", "IT");
        when(memberRepository.existsByMemberCodeIgnoreCase("STU101")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> memberService.registerMember(input));

        assertEquals("memberCode", ex.getField());
        assertTrue(ex.getMessage().contains("already registered"));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("Rejects an invalid email address")
    void rejectsInvalidEmail() {
        Member input = new Member("STU201", "Riya Sen", "riya.sen-at-college", "9876543210", "CSE");

        BusinessException ex = assertThrows(BusinessException.class, () -> memberService.registerMember(input));

        assertEquals("email", ex.getField());
        assertEquals("Please enter a valid email address", ex.getMessage());
        verifyNoInteractions(memberRepository);
    }

    @Test
    @DisplayName("Email validation accepts real addresses and rejects malformed ones")
    void validatesEmailFormat() {
        assertTrue(MemberService.isValidEmail("ananya.verma@student.libratrack.edu"));
        assertTrue(MemberService.isValidEmail("rohit+library@gmail.com"));
        assertFalse(MemberService.isValidEmail("rohit@"));
        assertFalse(MemberService.isValidEmail("rohit@gmail"));
        assertFalse(MemberService.isValidEmail("rohit gmail.com"));
        assertFalse(MemberService.isValidEmail(null));
    }

    @Test
    @DisplayName("Rejects a member with a required field left empty")
    void rejectsMissingRequiredField() {
        Member input = new Member("STU201", "  ", "riya@college.edu", "9876543210", "CSE");

        BusinessException ex = assertThrows(BusinessException.class, () -> memberService.registerMember(input));

        assertEquals("fullName", ex.getField());
        verifyNoInteractions(memberRepository);
    }

    @Test
    @DisplayName("Rejects a phone number that is not 10 digits")
    void rejectsInvalidPhone() {
        Member input = new Member("STU201", "Riya Sen", "riya@college.edu", "12345", "CSE");

        BusinessException ex = assertThrows(BusinessException.class, () -> memberService.registerMember(input));

        assertEquals("phone", ex.getField());
    }

    @Test
    @DisplayName("Finds a member by database id")
    void findsMemberById() {
        Member ananya = member(1L, "STU101", "Ananya Verma");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(ananya));

        assertSame(ananya, memberService.findMember(1L));
    }

    @Test
    @DisplayName("Finds a member by member ID, ignoring spaces")
    void findsMemberByMemberCode() {
        Member ananya = member(1L, "STU101", "Ananya Verma");
        when(memberRepository.findByMemberCodeIgnoreCase("STU101")).thenReturn(Optional.of(ananya));

        assertEquals("Ananya Verma", memberService.findByMemberCode(" STU101 ").getFullName());
    }

    @Test
    @DisplayName("Reports a missing member as not found")
    void throwsWhenMemberNotFound() {
        when(memberRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> memberService.findMember(404L));
    }

    @Test
    @DisplayName("Updates member information but keeps the original registration date")
    void updatesMemberInformation() {
        Member existing = member(1L, "STU101", "Ananya Verma");
        existing.setRegistrationDate(LocalDate.of(2026, 4, 12));
        Member changes = new Member("STU101", "Ananya Verma", "ananya.v@college.edu", "9123456789", "AI & DS");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(memberRepository.existsByMemberCodeIgnoreCaseAndIdNot("STU101", 1L)).thenReturn(false);
        when(memberRepository.existsByEmailIgnoreCaseAndIdNot("ananya.v@college.edu", 1L)).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member updated = memberService.updateMember(1L, changes);

        assertSame(existing, updated);
        assertEquals("ananya.v@college.edu", updated.getEmail());
        assertEquals("9123456789", updated.getPhone());
        assertEquals("AI & DS", updated.getDepartment());
        assertEquals(LocalDate.of(2026, 4, 12), updated.getRegistrationDate());
    }

    @Test
    @DisplayName("Rejects an update that reuses another member's email")
    void rejectsUpdateWithEmailOfAnotherMember() {
        Member existing = member(1L, "STU101", "Ananya Verma");
        Member changes = new Member("STU101", "Ananya Verma", "rohit.sharma@college.edu", "9123456789", "CSE");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(memberRepository.existsByMemberCodeIgnoreCaseAndIdNot("STU101", 1L)).thenReturn(false);
        when(memberRepository.existsByEmailIgnoreCaseAndIdNot("rohit.sharma@college.edu", 1L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> memberService.updateMember(1L, changes));

        assertEquals("email", ex.getField());
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("Prevents deleting a member who still has books")
    void preventsDeletionWithActiveLoans() {
        Member existing = member(1L, "STU101", "Ananya Verma");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(loanRepository.countByMemberIdAndReturnDateIsNull(1L)).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> memberService.deleteMember(1L));

        assertTrue(ex.getMessage().contains("2 books issued"));
        verify(memberRepository, never()).delete(any(Member.class));
    }

    private static Member member(Long id, String code, String name) {
        Member member = new Member(code, name, code.toLowerCase() + "@college.edu", "9876543210", "CSE");
        member.setId(id);
        return member;
    }
}
