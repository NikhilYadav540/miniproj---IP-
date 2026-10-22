# Smart Library Management System

## Architecture
    Browser (HTML + CSS + JS)  --fetch /api/...-->  Java server (port 8080)  --JDBC-->  MySQL (smart_library)
    frontend/                                         backend/                           database/schema.sql

Tables: categories 1--* books 1--* transactions *--1 users

## Run it
1. Install JDK 17+ and MySQL. The MySQL connector jar is already in `lib/`.
2. **Easy way (Windows):** double-click `start.bat` — it starts this project's own
   MySQL on port 3307 (data lives in `mysql_data\`, user `root` / password `root`)
   and then the server on port 8080.
3. **Manual way:** start MySQL on port 3307 as in `start.bat`, create the database
   `mysql -h 127.0.0.1 -P 3307 -u root -p < database/schema.sql`, check the port and
   password in `backend/Database.java`, then:
   - Windows:   `javac -cp "lib/*" -d out backend/*.java`  then  `java -cp "out;lib/*" Main`
   - Mac/Linux: `javac -cp "lib/*" -d out backend/*.java`  then  `java -cp "out:lib/*" Main`
4. Open http://localhost:8080
   - Admin: admin@library.com / admin123    - Member: nikhil@college.edu / pass123

Note: the Windows "MySQL80" service on port 3306 is a separate, older installation
whose password nobody remembers — this app does not use it.

## Demo order for the presentation
Login as member (catalog + search only) -> logout -> login as admin -> dashboard -> add a book ->
issue it -> see the available count drop -> return the overdue book (fine appears) -> members page.
