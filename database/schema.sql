-- =====================================================
-- Smart Library Management System : MySQL schema + sample data
-- Run once:  mysql -h 127.0.0.1 -P 3307 -u root -p < database/schema.sql
-- =====================================================
CREATE DATABASE IF NOT EXISTS smart_library;
USE smart_library;

-- Drop old tables (children first because of foreign keys)
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS books;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS categories;

-- 1. Categories: groups of books (Science, Fiction ...)
CREATE TABLE categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE
);

-- 2. Users: both admins (librarians) and members (students)
CREATE TABLE users (
    user_id   INT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(80)  NOT NULL,
    email     VARCHAR(100) NOT NULL UNIQUE,
    password  VARCHAR(100) NOT NULL,              -- plain text for the demo only
    role      ENUM('admin','member') NOT NULL DEFAULT 'member',
    joined_on DATE NOT NULL DEFAULT (CURDATE())
);

-- 3. Books: each book belongs to one category
CREATE TABLE books (
    book_id          INT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(150) NOT NULL,
    author           VARCHAR(100) NOT NULL,
    isbn             VARCHAR(20),
    category_id      INT NOT NULL,
    total_copies     INT NOT NULL DEFAULT 1,
    available_copies INT NOT NULL DEFAULT 1,
    FOREIGN KEY (category_id) REFERENCES categories(category_id),
    CHECK (total_copies >= 1),
    CHECK (available_copies >= 0 AND available_copies <= total_copies)
);

-- 4. Transactions: one row each time a book is issued (return_date is NULL until returned)
CREATE TABLE transactions (
    txn_id      INT AUTO_INCREMENT PRIMARY KEY,
    book_id     INT NOT NULL,
    user_id     INT NOT NULL,
    issue_date  DATE NOT NULL,
    due_date    DATE NOT NULL,
    return_date DATE NULL,
    fine        INT NOT NULL DEFAULT 0,
    FOREIGN KEY (book_id) REFERENCES books(book_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    CHECK (fine >= 0),
    INDEX idx_txn_open (user_id, return_date),
    INDEX idx_txn_book_open (book_id, return_date)
);

-- ---------------- Sample data ----------------
INSERT INTO categories (name) VALUES ('Computer Science'),('Science'),('Fiction'),('History'),('Self Help');

INSERT INTO users (name, email, password, role) VALUES
('Librarian', 'admin@library.com', 'admin123', 'admin'),
('Nikhil',    'nikhil@college.edu', 'pass123', 'member'),
('Ashok',     'ashok@college.edu',  'pass123', 'member'),
('Dhiraj',    'dhiraj@college.edu', 'pass123', 'member');

INSERT INTO books (title, author, isbn, category_id, total_copies, available_copies) VALUES
('Introduction to Algorithms',  'Thomas H. Cormen', '9780262046305', 1, 3, 2),
('Database System Concepts',    'Silberschatz',     '9780078022159', 1, 2, 1),
('Clean Code',                  'Robert C. Martin', '9780132350884', 1, 2, 2),
('A Brief History of Time',     'Stephen Hawking',  '9780553380163', 2, 2, 2),
('The Alchemist',               'Paulo Coelho',     '9780062315007', 3, 4, 4),
('Wings of Fire',               'A.P.J. Abdul Kalam','9788173711466', 4, 3, 3),
('Atomic Habits',               'James Clear',      '9780735211292', 5, 3, 3);

-- Three transactions (two still issued, one of them overdue; one returned 1 day late -> Rs 2 fine)
INSERT INTO transactions (book_id, user_id, issue_date, due_date, return_date, fine) VALUES
(1, 2, CURDATE() - INTERVAL 5 DAY,  CURDATE() + INTERVAL 9 DAY,  NULL, 0),
(2, 3, CURDATE() - INTERVAL 20 DAY, CURDATE() - INTERVAL 6 DAY,  NULL, 0),
(3, 4, CURDATE() - INTERVAL 30 DAY, CURDATE() - INTERVAL 16 DAY, CURDATE() - INTERVAL 15 DAY, 2);
