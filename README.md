# Health Care Distributed Database Management System

A distributed database system with one Coordinator and multiple Workers, supporting horizontal and vertical fragmentation, replication, and distributed query execution.

---

## Architecture

```
┌─────────────────┐
│   Coordinator   │  - Query distribution
│                 │  - Metadata management
│                 │  - Worker coordination
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
┌───▼───┐ ┌──▼────┐
│Worker1│ │Worker2│  - Query execution
│       │ │       │  - Data storage
│       │ │       │  - Local database management
└───────┘ └───────┘
```

### Components

**Coordinator**
- Distributes queries to appropriate workers
- Manages distribution metadata
- Tracks table fragmentation and replication
- Monitors worker health

**Workers**
- Execute SQL queries locally
- Manage local PostgreSQL databases
- Store table data
- Report status to coordinator

---

## Features

- ✅ **Distributed Query Execution**: Queries distributed across multiple workers
- ✅ **Horizontal Fragmentation**: Split tables by rows based on conditions
- ✅ **Vertical Fragmentation**: Split tables by columns
- ✅ **Table Replication**: Full replication across workers
- ✅ **Metadata Management**: Automatic tracking of data distribution
- ✅ **Health Monitoring**: Automatic worker health checks
- ✅ **Inline Fragmentation**: Configure fragmentation in CREATE TABLE command

---

## Quick Start

### Prerequisites
- Java 11 or higher
- Gradle (included via wrapper)

### 1. Build All Components
```bash
.\build-all.bat
```

### 2. Start Workers
```bash
# Terminal 1 - Worker 1
.\start-worker-1.bat

# Terminal 2 - Worker 2
.\start-worker-2.bat
```

### 3. Start Coordinator
```bash
# Terminal 3 - Coordinator
.\start-coordinator.bat
```

### 4. Run Commands
```sql
SQL> status
SQL> CREATE DATABASE shop;
SQL> USE shop;
SQL> CREATE TABLE products (id INT, name VARCHAR, price INT) ON worker-1, worker-2;
SQL> INSERT INTO products VALUES (1, 'Laptop', 1000);
SQL> SELECT * FROM products;
SQL> SHOW METADATA;
```

---

## Configuration

### Coordinator Configuration
**File**: `Coordinator/workers.config`
```
# Format: workerId,host,port
worker-1,localhost,8081
worker-2,localhost,8082
```

For distributed VMs:
```
worker-1,192.168.1.101,8081
worker-2,192.168.1.102,8082
```

### Worker Configuration
**File**: `Worker/worker.config`
```
workerId=worker-1
port=8081
```

---

## Usage Examples

### Basic Table Creation
```sql
CREATE DATABASE ecommerce;
USE ecommerce;

CREATE TABLE products (
    id INT,
    name VARCHAR,
    price INT
) ON worker-1, worker-2;

INSERT INTO products VALUES (1, 'Laptop', 1000);
SELECT * FROM products;
```

### Horizontal Fragmentation
```sql
-- Split orders by ID range
CREATE TABLE orders (
    id INT,
    customer_id INT,
    total INT
) ON worker-1, worker-2
  HORIZONTAL id RANGE 
    worker-1: id <= 1000, 
    worker-2: id > 1000;

-- Data automatically routed to correct worker
INSERT INTO orders VALUES (500, 1, 250);   -- Goes to worker-1
INSERT INTO orders VALUES (1500, 2, 350);  -- Goes to worker-2
```

### Vertical Fragmentation
```sql
-- Split user data by columns
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

INSERT INTO users VALUES (1, 'John', 'john@email.com', '123 St', '555-1234');
SELECT * FROM users;
```

### Table Replication
```sql
-- Create table
CREATE TABLE categories (id INT, name VARCHAR) ON worker-1, worker-2;

-- Enable full replication
REPLICATE TABLE categories ON worker-1, worker-2;

-- Data now stored on all workers
INSERT INTO categories VALUES (1, 'Electronics');
```

---

## Available Commands

See [COMMANDS.md](COMMANDS.md) for complete command reference.

### System Commands
- `status` - Show worker status
- `show metadata` - Display distribution metadata
- `help` - Show available commands
- `exit` / `quit` - Exit coordinator

### Database Commands
- `SHOW DATABASES`
- `CREATE DATABASE <name>`
- `USE <database>`
- `DROP DATABASE <name>`

### Table Commands
- `CREATE TABLE <name> (<columns>) ON <workers>`
- `CREATE TABLE ... HORIZONTAL ... RANGE ...`
- `CREATE TABLE ... VERTICAL ...`
- `DROP TABLE <name>`
- `DESCRIBE <name>`

### Data Commands
- `INSERT INTO <table> VALUES (...)`
- `SELECT * FROM <table>`
- `UPDATE <table> SET ... WHERE ...`
- `DELETE FROM <table> WHERE ...`

### Distribution Commands
- `REPLICATE TABLE <name> ON <workers>`
- `FRAGMENT TABLE <name> HORIZONTAL ...`
- `FRAGMENT TABLE <name> VERTICAL ...`

---

## Deployment

See [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) for detailed deployment instructions including:
- Local testing setup
- Multi-VM deployment
- Network configuration
- Firewall settings
- Production deployment

---

## Project Structure

```
DBMS_Project/
├── Coordinator/
│   ├── src/main/java/org/example/
│   │   ├── Main.java
│   │   ├── service/
│   │   │   ├── DistributedQueryCoordinator.java
│   │   │   ├── WorkerCommunicationService.java
│   │   │   ├── WorkerRegistry.java
│   │   │   └── MetadataManager.java
│   │   ├── model/
│   │   │   ├── WorkerNode.java
│   │   │   ├── DistributedMetadata.java
│   │   │   └── TableMetadata.java
│   │   └── ui/
│   │       └── ConsoleUserInterface.java
│   ├── data/db/metadata.json
│   ├── workers.config
│   └── build.gradle
│
├── Worker/
│   ├── src/main/java/org/example/
│   │   ├── Main.java
│   │   ├── server/
│   │   │   └── WorkerHttpServer.java
│   │   └── service/
│   │       └── PostgresqlQueryService.java
│   ├── worker.config
│   └── build.gradle
│
├── COMMANDS.md              # Command reference
├── DEPLOYMENT_GUIDE.md      # Deployment instructions
├── README.md                # This file
├── build-all.bat            # Build all components
├── start-coordinator.bat    # Start coordinator
├── start-worker-1.bat       # Start worker 1
└── start-worker-2.bat       # Start worker 2
```

---

## Key Features Explained

### Horizontal Fragmentation
- Splits table rows based on conditions
- Each worker stores a subset of rows
- Useful for range-based queries
- Example: Orders split by date or ID range

### Vertical Fragmentation
- Splits table columns across workers
- Each worker stores a subset of columns
- Primary key included in all fragments
- Useful for wide tables with column-based access

### Replication
- Full copy of table on all workers
- Useful for small, frequently-accessed tables
- Improves read performance
- Example: Categories, configuration tables

### Metadata Management
- Tracks table distribution automatically
- Stored in `Coordinator/data/db/metadata.json`
- Updated only when all workers succeed
- View with `SHOW METADATA` command

---

## Troubleshooting

### Workers Not Connecting
1. Check if workers are running: `status`
2. Verify `workers.config` has correct IPs and ports
3. Check firewall settings
4. Verify network connectivity

### "No database selected" Error
- Run `USE <database>;` before table operations

### "Worker not found or inactive" Error
- Check worker status with `status` command
- Restart inactive workers
- Verify worker configuration

### Metadata Not Updating
- Ensure all workers succeeded (check output)
- Verify `data/db/metadata.json` exists
- Restart coordinator if needed

---

## Development

### Building from Source
```bash
# Build coordinator
cd Coordinator
.\gradlew.bat build

# Build worker
cd Worker
.\gradlew.bat build
```

### Running Tests
```bash
cd Coordinator
.\gradlew.bat test

cd Worker
.\gradlew.bat test
```

---

## Contributors

- **Sanif Ali Momin** - Developer and Contributor
- **Tarun Chauhan** - Developer and Contributor

---

## License

This project is for educational purposes.

---

## Documentation

- [COMMANDS.md](COMMANDS.md) - Complete command reference with examples
- [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) - Deployment instructions for local and distributed setups

---

## Support

For issues or questions:
1. Check [COMMANDS.md](COMMANDS.md) for command syntax
2. Check [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) for setup issues
3. Verify worker status with `status` command
4. Check metadata with `SHOW METADATA` command
