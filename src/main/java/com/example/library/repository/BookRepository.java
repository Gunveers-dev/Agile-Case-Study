package com.example.library.repository;

import com.example.library.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    boolean existsByBookCodeIgnoreCase(String bookCode);

    boolean existsByBookCodeIgnoreCaseAndIdNot(String bookCode, Long id);

    Optional<Book> findByIsbn(String isbn);

    List<Book> findByTitleContainingIgnoreCase(String title);

    List<Book> findByAvailableQuantityGreaterThanOrderByTitleAsc(int quantity);

    /**
     * Free-text search over title, author, ISBN and book ID, optionally restricted to a category.
     * Pass empty strings (not null) to skip a filter.
     */
    @Query("""
            select b from Book b
            where (:q = ''
                   or lower(b.title) like lower(concat('%', :q, '%'))
                   or lower(b.author) like lower(concat('%', :q, '%'))
                   or lower(b.isbn) like lower(concat('%', :q, '%'))
                   or lower(b.bookCode) like lower(concat('%', :q, '%')))
              and (:category = '' or b.category = :category)
            order by b.bookCode asc
            """)
    List<Book> search(@Param("q") String q, @Param("category") String category);

    @Query("select coalesce(sum(b.totalQuantity), 0) from Book b")
    Long sumTotalQuantity();

    @Query("select coalesce(sum(b.availableQuantity), 0) from Book b")
    Long sumAvailableQuantity();

    @Query("select distinct b.category from Book b order by b.category")
    List<String> findDistinctCategories();

    /** Rows of [category, number of titles]. */
    @Query("select b.category, count(b.id) from Book b group by b.category order by count(b.id) desc")
    List<Object[]> countTitlesByCategory();
}
