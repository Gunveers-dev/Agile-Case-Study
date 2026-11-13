package com.example.library.service;

import com.example.library.dto.MemberRow;
import com.example.library.exception.BusinessException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.model.Loan;
import com.example.library.model.Member;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Member (student/staff) registration, profile maintenance and borrowing history.
 */
@Service
@Transactional(readOnly = true)
public class MemberService {

    public static final List<String> DEFAULT_DEPARTMENTS = List.of(
            "CSE", "IT", "AI & DS", "ECE", "EEE", "Mechanical", "Civil", "MCA", "MBA", "Faculty");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+91[\\- ]?)?[0-9]{10}$");

    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final Clock clock;

    public MemberService(MemberRepository memberRepository, LoanRepository loanRepository, Clock clock) {
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    public Page<MemberRow> findMembers(String query, String department, int page, int size) {
        List<Member> members = memberRepository.search(Pagination.clean(query), Pagination.clean(department));
        Map<Long, Long> activeLoans = activeLoansPerMember();
        List<MemberRow> rows = new ArrayList<>();
        for (Member member : members) {
            rows.add(new MemberRow(member, activeLoans.getOrDefault(member.getId(), 0L)));
        }
        return Pagination.paginate(rows, page, size);
    }

    public Member findMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Member", id));
    }

    public Member findByMemberCode(String memberCode) {
        return memberRepository.findByMemberCodeIgnoreCase(Pagination.clean(memberCode))
                .orElseThrow(() -> new ResourceNotFoundException("No member with ID " + memberCode));
    }

    public List<Member> findAllMembers() {
        return memberRepository.findAllByOrderByFullNameAsc();
    }

    public long countActiveLoans(Long memberId) {
        return loanRepository.countByMemberIdAndReturnDateIsNull(memberId);
    }

    public List<Loan> getBorrowingHistory(Long memberId) {
        return loanRepository.findByMemberIdOrderByIssueDateDescIdDesc(memberId);
    }

    /** Map of member id to books currently borrowed (for the Issue Book dropdown). */
    public Map<Long, Long> activeLoansPerMember() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : loanRepository.countActiveLoansPerMember()) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    public List<String> getDepartments() {
        Set<String> departments = new LinkedHashSet<>(DEFAULT_DEPARTMENTS);
        departments.addAll(memberRepository.findDistinctDepartments());
        return new ArrayList<>(departments);
    }

    /** Suggests the next free member ID, e.g. STU113. */
    public String nextMemberCode() {
        long number = 101 + memberRepository.count();
        String code = "STU" + number;
        while (memberRepository.existsByMemberCodeIgnoreCase(code)) {
            number++;
            code = "STU" + number;
        }
        return code;
    }

    // ------------------------------------------------------------------ commands

    /**
     * Registers a new member. Rules: required fields present, valid email and phone,
     * member ID and email not already registered.
     */
    @Transactional
    public Member registerMember(Member member) {
        if (member == null) {
            throw new BusinessException("Member details are required");
        }
        normalise(member);
        validate(member);
        if (memberRepository.existsByMemberCodeIgnoreCase(member.getMemberCode())) {
            throw new BusinessException("memberCode", "Member ID " + member.getMemberCode() + " is already registered");
        }
        if (memberRepository.existsByEmailIgnoreCase(member.getEmail())) {
            throw new BusinessException("email", "Email " + member.getEmail() + " is already registered");
        }
        member.setId(null);
        if (member.getRegistrationDate() == null) {
            member.setRegistrationDate(LocalDate.now(clock));
        }
        return memberRepository.save(member);
    }

    @Transactional
    public Member updateMember(Long id, Member changes) {
        Member existing = findMember(id);
        if (changes == null) {
            throw new BusinessException("Member details are required");
        }
        normalise(changes);
        validate(changes);
        if (memberRepository.existsByMemberCodeIgnoreCaseAndIdNot(changes.getMemberCode(), id)) {
            throw new BusinessException("memberCode", "Member ID " + changes.getMemberCode() + " is already registered");
        }
        if (memberRepository.existsByEmailIgnoreCaseAndIdNot(changes.getEmail(), id)) {
            throw new BusinessException("email", "Email " + changes.getEmail() + " is already registered");
        }
        existing.setMemberCode(changes.getMemberCode());
        existing.setFullName(changes.getFullName());
        existing.setEmail(changes.getEmail());
        existing.setPhone(changes.getPhone());
        existing.setDepartment(changes.getDepartment());
        return memberRepository.save(existing);
    }

    /**
     * Deletes a member. Refused while they still hold books; their returned-loan history is removed with them.
     */
    @Transactional
    public void deleteMember(Long id) {
        Member member = findMember(id);
        long activeLoans = loanRepository.countByMemberIdAndReturnDateIsNull(id);
        if (activeLoans > 0) {
            throw new BusinessException("Cannot delete " + member.getFullName() + ": they still have "
                    + activeLoans + (activeLoans == 1 ? " book" : " books") + " issued. Return them first.");
        }
        loanRepository.deleteHistoryForMember(id);
        memberRepository.delete(member);
    }

    // ------------------------------------------------------------------ helpers

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    private static void validate(Member member) {
        if (isBlank(member.getMemberCode())) {
            throw new BusinessException("memberCode", "Member ID is required");
        }
        if (isBlank(member.getFullName())) {
            throw new BusinessException("fullName", "Full name is required");
        }
        if (isBlank(member.getEmail())) {
            throw new BusinessException("email", "Email is required");
        }
        if (!isValidEmail(member.getEmail())) {
            throw new BusinessException("email", "Please enter a valid email address");
        }
        if (isBlank(member.getPhone())) {
            throw new BusinessException("phone", "Phone number is required");
        }
        if (!PHONE_PATTERN.matcher(member.getPhone()).matches()) {
            throw new BusinessException("phone", "Enter a 10-digit phone number");
        }
        if (isBlank(member.getDepartment())) {
            throw new BusinessException("department", "Department is required");
        }
    }

    private static void normalise(Member member) {
        String code = trim(member.getMemberCode());
        member.setMemberCode(code == null ? null : code.toUpperCase());
        member.setFullName(trim(member.getFullName()));
        String email = trim(member.getEmail());
        member.setEmail(email == null ? null : email.toLowerCase());
        member.setPhone(trim(member.getPhone()));
        member.setDepartment(trim(member.getDepartment()));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
