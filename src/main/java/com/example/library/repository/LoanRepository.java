package com.example.library.repository;

import com.example.library.model.Loan;
import com.example.library.model.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    // ---------- counts ----------

    /** Loans whose book has not been returned yet (issued + overdue). */
    long countByReturnDateIsNull();

    /** Loans not returned whose due date is before the given day. */
    long countByReturnDateIsNullAndDueDateBefore(LocalDate date);

    long countByReturnDateIsNotNull();

    long countByMemberIdAndReturnDateIsNull(Long memberId);

    long countByBookIdAndReturnDateIsNull(Long bookId);

    boolean existsByMemberIdAndBookIdAndReturnDateIsNull(Long memberId, Long bookId);

    // ---------- lists ----------

    List<Loan> findByReturnDateIsNullAndDueDateBeforeOrderByDueDateAsc(LocalDate date);

    List<Loan> findByMemberIdOrderByIssueDateDescIdDesc(Long memberId);

    List<Loan> findByBookIdOrderByIssueDateDescIdDesc(Long bookId);

    List<Loan> findTop5ByOrderByIssueDateDescIdDesc();

    List<Loan> findTop5ByReturnDateIsNotNullOrderByReturnDateDescIdDesc();

    /** Books currently out, optionally filtered by member ID/name or book title/ID. */
    @Query("""
            select l from Loan l join l.member m join l.book b
            where l.returnDate is null
              and (:q = ''
                   or lower(m.memberCode) like lower(concat('%', :q, '%'))
                   or lower(m.fullName) like lower(concat('%', :q, '%'))
                   or lower(b.title) like lower(concat('%', :q, '%'))
                   or lower(b.bookCode) like lower(concat('%', :q, '%')))
            order by l.dueDate asc, l.id asc
            """)
    List<Loan> searchActiveLoans(@Param("q") String q);

    /**
     * Transaction history with search, status and issue-date filters.
     * status is '' (all), 'ISSUED', 'OVERDUE' or 'RETURNED'; overdue is derived from the due date.
     */
    @Query(value = """
            select l from Loan l join l.member m join l.book b
            where (:q = ''
                   or lower(m.fullName) like lower(concat('%', :q, '%'))
                   or lower(m.memberCode) like lower(concat('%', :q, '%'))
                   or lower(b.title) like lower(concat('%', :q, '%'))
                   or lower(b.bookCode) like lower(concat('%', :q, '%')))
              and l.issueDate >= :fromDate and l.issueDate <= :toDate
              and (:status = ''
                   or (:status = 'RETURNED' and l.returnDate is not null)
                   or (:status = 'OVERDUE' and l.returnDate is null and l.dueDate < :today)
                   or (:status = 'ISSUED' and l.returnDate is null and l.dueDate >= :today))
            """,
            countQuery = """
            select count(l) from Loan l join l.member m join l.book b
            where (:q = ''
                   or lower(m.fullName) like lower(concat('%', :q, '%'))
                   or lower(m.memberCode) like lower(concat('%', :q, '%'))
                   or lower(b.title) like lower(concat('%', :q, '%'))
                   or lower(b.bookCode) like lower(concat('%', :q, '%')))
              and l.issueDate >= :fromDate and l.issueDate <= :toDate
              and (:status = ''
                   or (:status = 'RETURNED' and l.returnDate is not null)
                   or (:status = 'OVERDUE' and l.returnDate is null and l.dueDate < :today)
                   or (:status = 'ISSUED' and l.returnDate is null and l.dueDate >= :today))
            """)
    Page<Loan> searchTransactions(@Param("q") String q,
                                  @Param("status") String status,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate,
                                  @Param("today") LocalDate today,
                                  Pageable pageable);

    // ---------- analytics ----------

    @Query("select l.issueDate from Loan l where l.issueDate >= :from")
    List<LocalDate> findIssueDatesSince(@Param("from") LocalDate from);

    /** Rows of [returnDate, fineAmount] for returned loans that carried a fine. */
    @Query("select l.returnDate, l.fineAmount from Loan l where l.returnDate >= :from and l.fineAmount > 0")
    List<Object[]> findFinesCollectedSince(@Param("from") LocalDate from);

    /** Rows of [bookId, number of loans], most borrowed first. */
    @Query("select l.book.id, count(l.id) from Loan l group by l.book.id order by count(l.id) desc")
    List<Object[]> countLoansPerBook(Pageable pageable);

    /** Rows of [bookId, number of active loans]. */
    @Query("select l.book.id, count(l.id) from Loan l where l.returnDate is null group by l.book.id")
    List<Object[]> countActiveLoansPerBook();

    /** Rows of [memberId, number of active loans]. */
    @Query("select l.member.id, count(l.id) from Loan l where l.returnDate is null group by l.member.id")
    List<Object[]> countActiveLoansPerMember();

    @Query("select coalesce(sum(l.fineAmount), 0) from Loan l where l.returnDate is not null")
    BigDecimal sumCollectedFines();

    // ---------- maintenance ----------

    /** Marks ISSUED loans past their due date as OVERDUE. Returns the number of loans updated. */
    @Modifying
    @Query("""
            update Loan l set l.status = :overdue
            where l.returnDate is null and l.status = :issued and l.dueDate < :today
            """)
    int markOverdue(@Param("today") LocalDate today,
                    @Param("issued") LoanStatus issued,
                    @Param("overdue") LoanStatus overdue);

    /** Removes the (returned) loan history of a book that is being deleted. */
    @Modifying
    @Query("delete from Loan l where l.book.id = :bookId")
    int deleteHistoryForBook(@Param("bookId") Long bookId);

    /** Removes the (returned) loan history of a member who is being deleted. */
    @Modifying
    @Query("delete from Loan l where l.member.id = :memberId")
    int deleteHistoryForMember(@Param("memberId") Long memberId);
}
