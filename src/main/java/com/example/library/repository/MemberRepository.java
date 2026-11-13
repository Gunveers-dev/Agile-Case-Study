package com.example.library.repository;

import com.example.library.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByMemberCodeIgnoreCase(String memberCode);

    boolean existsByMemberCodeIgnoreCaseAndIdNot(String memberCode, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Optional<Member> findByMemberCodeIgnoreCase(String memberCode);

    List<Member> findAllByOrderByFullNameAsc();

    List<Member> findTop5ByOrderByRegistrationDateDescIdDesc();

    /**
     * Free-text search over name, member ID, email and phone, optionally restricted to a department.
     * Pass empty strings (not null) to skip a filter.
     */
    @Query("""
            select m from Member m
            where (:q = ''
                   or lower(m.fullName) like lower(concat('%', :q, '%'))
                   or lower(m.memberCode) like lower(concat('%', :q, '%'))
                   or lower(m.email) like lower(concat('%', :q, '%'))
                   or m.phone like concat('%', :q, '%'))
              and (:department = '' or m.department = :department)
            order by m.memberCode asc
            """)
    List<Member> search(@Param("q") String q, @Param("department") String department);

    @Query("select distinct m.department from Member m order by m.department")
    List<String> findDistinctDepartments();
}
