package com.example.library.config;

import com.example.library.model.Book;
import com.example.library.model.Loan;
import com.example.library.model.LoanStatus;
import com.example.library.model.Member;
import com.example.library.repository.BookRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.MemberRepository;
import com.example.library.service.AdminService;
import com.example.library.service.FineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Inserts the demo administrator and sample data on the first start.
 * <p>
 * The administrator is created if missing. Books, members and loans are inserted only when all three
 * tables are empty, so restarting the application never duplicates sample data.
 * Dates are relative to "today" so the dashboard always looks current during a demonstration.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    public static final String DEMO_ADMIN_EMAIL = "admin@libratrack.com";
    public static final String DEMO_ADMIN_PASSWORD = "admin123";

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AdminService adminService;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final FineService fineService;
    private final Clock clock;
    private final boolean seedDemoData;

    public DataInitializer(AdminService adminService,
                           BookRepository bookRepository,
                           MemberRepository memberRepository,
                           LoanRepository loanRepository,
                           FineService fineService,
                           Clock clock,
                           @Value("${libratrack.seed-demo-data:true}") boolean seedDemoData) {
        this.adminService = adminService;
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.fineService = fineService;
        this.clock = clock;
        this.seedDemoData = seedDemoData;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (adminService.createAdminIfMissing(DEMO_ADMIN_EMAIL, DEMO_ADMIN_PASSWORD, "Library Admin")) {
            log.info("Created demo administrator {}", DEMO_ADMIN_EMAIL);
        }

        boolean databaseEmpty = bookRepository.count() == 0
                && memberRepository.count() == 0
                && loanRepository.count() == 0;
        if (!seedDemoData || !databaseEmpty) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        List<Book> books = seedBooks(today);
        List<Member> members = seedMembers(today);
        int loans = seedLoans(today, books, members);
        log.info("Inserted sample data: {} books, {} members, {} loans", books.size(), members.size(), loans);
    }

    // ------------------------------------------------------------------ books

    private List<Book> seedBooks(LocalDate today) {
        String[][] data = {
                // code, title, author, isbn, category, publisher, quantity
                {"BK001", "Clean Code", "Robert C. Martin", "9780132350884", "Programming", "Prentice Hall", "8"},
                {"BK002", "Design Patterns", "Erich Gamma", "9780201633610", "Programming", "Addison-Wesley", "5"},
                {"BK003", "Effective Java", "Joshua Bloch", "9780134685991", "Programming", "Addison-Wesley", "6"},
                {"BK004", "The Pragmatic Programmer", "Andrew Hunt & David Thomas", "9780135957059", "Programming", "Addison-Wesley", "4"},
                {"BK005", "Head First Java", "Kathy Sierra & Bert Bates", "9780596009205", "Programming", "O'Reilly Media", "5"},
                {"BK006", "Artificial Intelligence: A Modern Approach", "Stuart Russell & Peter Norvig", "9780136042594", "AI & Machine Learning", "Pearson", "3"},
                {"BK007", "Hands-On Machine Learning", "Aurelien Geron", "9781492032649", "AI & Machine Learning", "O'Reilly Media", "4"},
                {"BK008", "Deep Learning", "Ian Goodfellow", "9780262035613", "AI & Machine Learning", "MIT Press", "3"},
                {"BK009", "Pattern Recognition and Machine Learning", "Christopher M. Bishop", "9780387310732", "AI & Machine Learning", "Springer", "2"},
                {"BK010", "Introduction to Algorithms", "Thomas H. Cormen", "9780262033848", "Data Structures", "MIT Press", "6"},
                {"BK011", "Data Structures and Algorithms in Java", "Robert Lafore", "9780672324536", "Data Structures", "Sams Publishing", "4"},
                {"BK012", "Algorithms", "Robert Sedgewick & Kevin Wayne", "9780321573513", "Data Structures", "Addison-Wesley", "3"},
                {"BK013", "Database System Concepts", "Abraham Silberschatz", "9780073523323", "Database Management", "McGraw-Hill", "5"},
                {"BK014", "Fundamentals of Database Systems", "Ramez Elmasri & Shamkant Navathe", "9780133970777", "Database Management", "Pearson", "4"},
                {"BK015", "SQL: The Complete Reference", "James R. Groff", "9780071592550", "Database Management", "McGraw-Hill", "2"},
                {"BK016", "Discrete Mathematics and Its Applications", "Kenneth H. Rosen", "9780073383095", "Mathematics", "McGraw-Hill", "5"},
                {"BK017", "Higher Engineering Mathematics", "B. S. Grewal", "9788174091956", "Mathematics", "Khanna Publishers", "6"},
                {"BK018", "Linear Algebra and Its Applications", "Gilbert Strang", "9780030105678", "Mathematics", "Cengage", "3"},
                {"BK019", "Computer Networks", "Andrew S. Tanenbaum", "9780132126953", "Computer Networks", "Pearson", "6"},
                {"BK020", "Computer Networking: A Top-Down Approach", "James Kurose & Keith Ross", "9780133594140", "Computer Networks", "Pearson", "4"},
        };
        List<Book> books = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            String[] row = data[i];
            Book book = new Book(row[0], row[1], row[2], row[3], row[4], row[5], Integer.parseInt(row[6]));
            book.setCreatedAt(today.minusDays(240L - i * 4L).atTime(10, 0).plusMinutes(i * 7L));
            books.add(book);
        }
        // Both copies of the SQL reference are away for rebinding: shows the "Out of Stock" badge.
        books.get(14).setAvailableQuantity(0);
        return bookRepository.saveAll(books);
    }

    // ------------------------------------------------------------------ members

    private List<Member> seedMembers(LocalDate today) {
        String[][] data = {
                {"STU101", "Ananya Verma", "CSE"},
                {"STU102", "Rohit Sharma", "IT"},
                {"STU103", "Karan Mehta", "CSE"},
                {"STU104", "Priya Nair", "AI & DS"},
                {"STU105", "Arjun Singh", "ECE"},
                {"STU106", "Sneha Kapoor", "CSE"},
                {"STU107", "Vikram Rao", "Mechanical"},
                {"STU108", "Isha Gupta", "MCA"},
                {"STU109", "Aditya Kulkarni", "IT"},
                {"STU110", "Meera Iyer", "AI & DS"},
                {"STU111", "Rahul Bansal", "EEE"},
                {"STU112", "Neha Joshi", "MBA"},
        };
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            String[] row = data[i];
            String email = row[1].toLowerCase().replace(' ', '.') + "@student.libratrack.edu";
            String phone = "98" + String.format("%08d", 14_520_316L + i * 1_093_771L);
            Member member = new Member(row[0], row[1], email, phone, row[2]);
            // First ten joined over the past months; the last two joined this week.
            long daysAgo = i < 10 ? 210L - i * 15L : (i == 10 ? 3L : 1L);
            member.setRegistrationDate(today.minusDays(daysAgo));
            members.add(member);
        }
        return memberRepository.saveAll(members);
    }

    // ------------------------------------------------------------------ loans

    private int seedLoans(LocalDate today, List<Book> books, List<Member> members) {
        List<Loan> loans = new ArrayList<>();
        Random random = new Random(2026);

        // Returned loans spread over the last six months (gives the issue-trend chart its shape).
        int[] returnedPerMonth = {6, 8, 11, 9, 13, 3};
        int[] popularity = {4, 2, 3, 2, 2, 4, 3, 2, 1, 4, 2, 1, 4, 2, 1, 2, 3, 1, 4, 2};
        List<Integer> weightedBooks = new ArrayList<>();
        for (int b = 0; b < popularity.length; b++) {
            for (int w = 0; w < popularity[b]; w++) {
                weightedBooks.add(b);
            }
        }
        YearMonth currentMonth = YearMonth.from(today);
        for (int m = 0; m < returnedPerMonth.length; m++) {
            YearMonth month = currentMonth.minusMonths(returnedPerMonth.length - 1L - m);
            int lastDay = month.equals(currentMonth) ? Math.max(1, today.getDayOfMonth() - 1) : month.lengthOfMonth();
            for (int n = 0; n < returnedPerMonth[m]; n++) {
                LocalDate issueDate = month.atDay(1 + random.nextInt(lastDay));
                Book book = books.get(weightedBooks.get(random.nextInt(weightedBooks.size())));
                Member member = pickMemberRegisteredBy(members, issueDate, random);
                LocalDate dueDate = issueDate.plusDays(14);
                LocalDate returnDate = issueDate.plusDays(5 + random.nextInt(18));
                if (returnDate.isAfter(today)) {
                    returnDate = today;
                }
                Loan loan = new Loan(member, book, issueDate, dueDate);
                loan.setReturnDate(returnDate);
                loan.setFineAmount(fineService.calculateFine(issueDate, dueDate, returnDate));
                loan.setStatus(LoanStatus.RETURNED);
                loans.add(loan);
            }
        }

        // Books currently out: {member index, book index, days since issue}.
        // Loans issued more than 14 days ago are overdue. Ananya (STU101) holds 3 books = borrowing limit.
        int[][] active = {
                {1, 0, 0},    // Rohit Sharma - Clean Code (today)
                {11, 7, 0},   // Neha Joshi - Deep Learning
                {0, 12, 1},   // Ananya Verma - Database System Concepts
                {10, 11, 2},  // Rahul Bansal - Algorithms
                {8, 13, 3},   // Aditya Kulkarni - Fundamentals of Database Systems
                {2, 9, 4},    // Karan Mehta - Introduction to Algorithms
                {0, 19, 5},   // Ananya Verma - Computer Networking: A Top-Down Approach
                {3, 6, 6},    // Priya Nair - Hands-On Machine Learning
                {9, 5, 7},    // Meera Iyer - AI: A Modern Approach
                {4, 18, 9},   // Arjun Singh - Computer Networks
                {5, 5, 11},   // Sneha Kapoor - AI: A Modern Approach
                {7, 5, 17},   // Isha Gupta - AI: A Modern Approach (overdue 3 days)
                {0, 2, 20},   // Ananya Verma - Effective Java (overdue 6 days)
                {6, 16, 24},  // Vikram Rao - Higher Engineering Mathematics (overdue 10 days)
        };
        for (int[] entry : active) {
            Member member = members.get(entry[0]);
            Book book = books.get(entry[1]);
            LocalDate issueDate = today.minusDays(entry[2]);
            Loan loan = new Loan(member, book, issueDate, issueDate.plusDays(14));
            loan.setStatus(loan.isOverdueOn(today) ? LoanStatus.OVERDUE : LoanStatus.ISSUED);
            book.setAvailableQuantity(book.getAvailableQuantity() - 1);
            loans.add(loan);
        }

        bookRepository.saveAll(books);
        loanRepository.saveAll(loans);
        return loans.size();
    }

    private static Member pickMemberRegisteredBy(List<Member> members, LocalDate date, Random random) {
        List<Member> eligible = new ArrayList<>();
        for (Member member : members) {
            if (!member.getRegistrationDate().isAfter(date)) {
                eligible.add(member);
            }
        }
        if (eligible.isEmpty()) {
            return members.get(0);
        }
        return eligible.get(random.nextInt(eligible.size()));
    }
}
