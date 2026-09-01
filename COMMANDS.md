# Database Commands Reference

## System Commands

```sql
status              -- Show worker status and health
show metadata       -- Display distribution metadata
help                -- Show available commands
exit / quit         -- Exit coordinator
```

---

## Database Commands

### Show Databases
```sql
SHOW DATABASES;
```

### Create Database
```sql
CREATE DATABASE <database_name>;
```
**Example:**
```sql
CREATE DATABASE shop;
```

### Use Database
```sql
USE <database_name>;
```
**Example:**
```sql
USE shop;
```

### Drop Database
```sql
DROP DATABASE <database_name>;
```
**Example:**
```sql
DROP DATABASE shop;
```

---

## Table Commands

### Create Table (Basic)
**Syntax:**
```sql
CREATE TABLE <table_name> (<column_definitions>) ON <worker1>, <worker2>;
```

**Example:**
```sql
CREATE TABLE products (
    id INT,
    name VARCHAR,
    price INT
) ON worker-1, worker-2;
```

### Create Table with Horizontal Fragmentation
**Syntax:**
```sql
CREATE TABLE <table_name> (<column_definitions>) ON <worker1>, <worker2>
  HORIZONTAL <column_name> RANGE 
    <worker1>: <condition1>, 
    <worker2>: <condition2>;
```

**Example:**
```sql
CREATE TABLE orders (
    id INT,
    customer_id INT,
    total INT,
    order_date VARCHAR
) ON worker-1, worker-2
  HORIZONTAL id RANGE 
    worker-1: id >= 1 AND id <= 1000, 
    worker-2: id > 1000;
```

### Create Table with Vertical Fragmentation
**Syntax:**
```sql
CREATE TABLE <table_name> (<column_definitions>) ON <worker1>, <worker2>
  VERTICAL 
    <worker1>: <col1>,<col2>,<col3>, 
    <worker2>: <col4>,<col5>;
```

**Example:**
```sql
CREATE TABLE users (
    id INT,
    name VARCHAR,
    email VARCHAR,
    address VARCHAR,
    phone VARCHAR
) ON worker-1, worker-2
  VERTICAL 
    worker-1: id,name,email, 
    worker-2: id,address,phone;
```

### Drop Table
```sql
DROP TABLE <table_name>;
```
**Example:**
```sql
DROP TABLE products;
```

### Describe Table
```sql
DESCRIBE <table_name>;
```
**Example:**
```sql
DESCRIBE products;
```

---

## Data Manipulation Commands

### Insert Data
**Syntax:**
```sql
INSERT INTO <table_name> VALUES (<value1>, <value2>, ...);
```

**Example:**
```sql
INSERT INTO products VALUES (1, 'Laptop', 1000);
INSERT INTO orders VALUES (500, 101, 250, '2024-01-15');
```

**INSERT Routing Behavior:**
The coordinator intelligently routes INSERT operations based on table metadata:

- **Replicated Tables**: INSERT sent to ALL workers
  ```sql
  REPLICATE TABLE users;
  INSERT INTO users VALUES (1, 'Alice', 100);  -- Goes to all workers
  ```

- **Horizontal Fragmentation**: INSERT sent to ONE worker based on fragment key
  ```sql
  -- Table fragmented: worker-1 (id<=1000), worker-2 (id>1000)
  INSERT INTO orders VALUES (500, 100);   -- Goes to worker-1
  INSERT INTO orders VALUES (1500, 200);  -- Goes to worker-2
  ```

- **Vertical Fragmentation**: INSERT sent to ALL workers (each stores its columns)
  ```sql
  -- worker-1 has (id,name), worker-2 has (id,address)
  INSERT INTO users VALUES (1, 'Alice', '123 Main St');  -- Both workers get data
  ```

- **No Distribution Strategy**: INSERT sent to FIRST worker only
  ```sql
  -- Table exists on worker-1, worker-2 but no REPLICATE/FRAGMENT
  INSERT INTO products VALUES (1, 'Laptop');  -- Goes to worker-1 only
  ```

See `INSERT_ROUTING_GUIDE.md` for detailed routing logic.

### Select Data
**Syntax:**
```sql
SELECT * FROM <table_name>;
SELECT <column1>, <column2> FROM <table_name>;
SELECT * FROM <table_name> WHERE <condition>;
```

**Examples:**
```sql
SELECT * FROM products;
SELECT name, price FROM products;
SELECT * FROM products WHERE price > 500;
SELECT * FROM orders WHERE id <= 1000;
```

### Update Data
**Syntax:**
```sql
UPDATE <table_name> SET <column>=<value> WHERE <condition>;
```

**Example:**
```sql
UPDATE products SET price = 1200 WHERE id = 1;
UPDATE orders SET total = 300 WHERE id = 500;
```

### Delete Data
**Syntax:**
```sql
DELETE FROM <table_name> WHERE <condition>;
```

**Example:**
```sql
DELETE FROM products WHERE id = 1;
DELETE FROM orders WHERE id > 1000;
```

---

## Distribution Commands

### Replicate Table
**Syntax:**
```sql
REPLICATE TABLE <table_name> ON <worker1>, <worker2>;
```

**Example:**
```sql
REPLICATE TABLE categories ON worker-1, worker-2;
```

**Note:** This enables full replication. All data will be stored on all specified workers.

### Fragment Table (Horizontal)
**Syntax:**
```sql
FRAGMENT TABLE <table_name> HORIZONTAL ON <column_name>
  RANGE 
    <worker1>: <condition1>, 
    <worker2>: <condition2>;
```

**Example:**
```sql
FRAGMENT TABLE orders HORIZONTAL ON id
  RANGE 
    worker-1: id >= 1 AND id <= 1000, 
    worker-2: id > 1000;
```

### Fragment Table (Vertical)
**Syntax:**
```sql
FRAGMENT TABLE <table_name> VERTICAL
  COLUMNS 
    <worker1>: <col1>,<col2>, 
    <worker2>: <col3>,<col4>;
```

**Example:**
```sql
FRAGMENT TABLE users VERTICAL
  COLUMNS 
    worker-1: id,name,email, 
    worker-2: id,address,phone;
```

---

## Complete Workflow Examples

### Example 1: E-commerce Database

```sql
-- Create and use database
CREATE DATABASE ecommerce;
USE ecommerce;

-- Create categories table (small, will replicate)
CREATE TABLE categories (
    id INT,
    name VARCHAR
) ON worker-1, worker-2;

-- Enable replication for categories
REPLICATE TABLE categories ON worker-1, worker-2;

-- Create products table (basic, no fragmentation)
CREATE TABLE products (
    id INT,
    name VARCHAR,
    category_id INT,
    price INT
) ON worker-1, worker-2;

-- Create orders table with horizontal fragmentation
CREATE TABLE orders (
    id INT,
    customer_id INT,
    total INT,
    order_date VARCHAR
) ON worker-1, worker-2
  HORIZONTAL id RANGE 
    worker-1: id >= 1 AND id <= 10000, 
    worker-2: id > 10000;

-- Create customers table with vertical fragmentation
CREATE TABLE customers (
    id INT,
    name VARCHAR,
    email VARCHAR,
    address VARCHAR,
    phone VARCHAR
) ON worker-1, worker-2
  VERTICAL 
    worker-1: id,name,email, 
    worker-2: id,address,phone;

-- Insert data
INSERT INTO categories VALUES (1, 'Electronics');
INSERT INTO products VALUES (1, 'Laptop', 1, 1000);
INSERT INTO orders VALUES (500, 1, 1000, '2024-01-15');
INSERT INTO orders VALUES (15000, 2, 2000, '2024-01-16');
INSERT INTO customers VALUES (1, 'John Doe', 'john@email.com', '123 Main St', '555-1234');

-- Query data
SELECT * FROM categories;
SELECT * FROM products;
SELECT * FROM orders;
SELECT * FROM customers;

-- Check distribution
SHOW METADATA;
```

### Example 2: Social Media Platform

```sql
-- Setup
CREATE DATABASE social;
USE social;

-- Users table (replicated - frequently accessed)
CREATE TABLE users (
    id INT,
    username VARCHAR,
    email VARCHAR
) ON worker-1, worker-2;

REPLICATE TABLE users ON worker-1, worker-2;

-- Posts table (horizontal fragmentation by ID)
CREATE TABLE posts (
    id INT,
    user_id INT,
    content VARCHAR,
    created_at VARCHAR
) ON worker-1, worker-2
  HORIZONTAL id RANGE 
    worker-1: id >= 1 AND id <= 100000, 
    worker-2: id > 100000;

-- User profiles (vertical fragmentation)
CREATE TABLE user_profiles (
    user_id INT,
    bio VARCHAR,
    avatar VARCHAR,
    settings VARCHAR,
    preferences VARCHAR
) ON worker-1, worker-2
  VERTICAL 
    worker-1: user_id,bio,avatar, 
    worker-2: user_id,settings,preferences;

-- Insert and query
INSERT INTO users VALUES (1, 'alice', 'alice@social.com');
INSERT INTO posts VALUES (500, 1, 'Hello World', '2024-01-15');
INSERT INTO user_profiles VALUES (1, 'Software Engineer', 'avatar.jpg', '{}', '{}');

SELECT * FROM users;
SELECT * FROM posts;
SELECT * FROM user_profiles;
```

---

## Important Notes

1. **Database Context Required**: Always use `USE <database>;` before creating tables or manipulating data.

2. **Worker Specification Required**: CREATE TABLE must include the `ON worker1, worker2` clause.

3. **No Default Replication**: Tables are NOT replicated by default. Use `REPLICATE TABLE` to enable replication.

4. **Metadata Updates**: Metadata is only updated if ALL workers succeed in executing the command.

5. **Worker Availability**: All specified workers must be active before operations.

6. **Fragmentation Types**:
   - **HORIZONTAL**: Splits rows based on conditions (range-based)
   - **VERTICAL**: Splits columns across workers
   - **NONE**: Table exists on specified workers without fragmentation

7. **Primary Keys**: For vertical fragmentation, include the primary key (usually `id`) in all fragments for joins.

---

## Error Messages

### "No database selected"
**Solution**: Run `USE <database>;` before the command.

### "Worker not found or inactive"
**Solution**: Check worker status with `status` command and verify `workers.config`.

### "Invalid CREATE TABLE syntax"
**Solution**: Ensure the command includes the `ON worker1, worker2` clause.

---

## Tips

1. Use `status` to check worker health before operations
2. Use `SHOW METADATA` to verify table distribution
3. Start with basic tables, then add fragmentation as needed
4. Replicate small, frequently-accessed tables
5. Use horizontal fragmentation for large tables with range-based access
6. Use vertical fragmentation for wide tables with column-based access
