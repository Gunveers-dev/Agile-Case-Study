package com.example.library.dto;

import com.example.library.model.Member;

/**
 * A member as displayed in the members table, with the number of books currently borrowed.
 */
public record MemberRow(Member member, long activeLoans) {
}
