# Distributed Database Deployment Guide

## System Overview
- **Coordinator**: 1 instance - manages query distribution
- **Workers**: 2 instances - execute queries and store data
- **Communication**: HTTP-based (REST API)

## Quick Start - Local Testing

### Terminal 1 - Worker 1
```bash
cd Worker
gradlew run --args="worker-1 8081"
```

### Terminal 2 - Worker 2
```bash
cd Worker
gradlew run --args="worker-2 8082"
```

### Terminal 3 - Coordinator
```bash
cd Coordinator
# Edit workers.config to use localhost
echo "worker-1,localhost,8081" > workers.config
echo "worker-2,localhost,8082" >> workers.config
gradlew run
```

## Production Deployment - Different VMs

### VM Requirements
- **Coordinator VM**: 2GB RAM, 1 CPU
- **Worker VMs**: 4GB RAM, 2 CPU (each)
- **OS**: Linux (Ubuntu 20.04+) or Windows Server
- **Java**: OpenJDK 11 or higher
- **Network**: All VMs must be able to communicate

### Network Configuration

| VM | Role | IP Example | Port | Firewall |
|----|------|------------|------|----------|
| VM1 | Coordinator | 192.168.1.100 | - | Outbound to workers |
| VM2 | Worker-1 | 192.168.1.101 | 8081 | Inbound 8081 |
| VM3 | Worker-2 | 192.168.1.102 | 8082 | Inbound 8082 |

### Step-by-Step Deployment

#### 1. Prepare All VMs

On each VM:
```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Java
sudo apt install openjdk-11-jdk -y

# Verify
java -version
```

#### 2. Deploy Worker on VM2 (192.168.1.101)

```bash
# Transfer files
scp -r Worker user@192.168.1.101:/home/user/

# SSH to VM2
ssh user@192.168.1.101

# Navigate to Worker
cd Worker

# Configure
echo "worker-1,8081" > worker.config

# Build
./gradlew build

# Open firewall
sudo ufw allow 8081/tcp
sudo ufw reload

# Run worker
java -jar build/libs/Worker-1.0-SNAPSHOT.jar worker-1 8081
```

#### 3. Deploy Worker on VM3 (192.168.1.102)

```bash
# Transfer files
scp -r Worker user@192.168.1.102:/home/user/

# SSH to VM3
ssh user@192.168.1.102

# Navigate to Worker
cd Worker

# Configure
echo "worker-2,8082" > worker.config

# Build
./gradlew build

# Open firewall
sudo ufw allow 8082/tcp
sudo ufw reload

# Run worker
java -jar build/libs/Worker-1.0-SNAPSHOT.jar worker-2 8082
```

#### 4. Deploy Coordinator on VM1 (192.168.1.100)

```bash
# Transfer files
scp -r Coordinator user@192.168.1.100:/home/user/

# SSH to VM1
ssh user@192.168.1.100

# Navigate to Coordinator
cd Coordinator

# Configure workers
cat > workers.config << EOF
worker-1,192.168.1.101,8081
worker-2,192.168.1.102,8082
EOF

# Build
./gradlew build

# Run coordinator
java -jar build/libs/Coordinator-1.0-SNAPSHOT.jar
```

### 5. Verify Deployment

On Coordinator, you should see:
```
=== Distributed Database Coordinator ===

Worker registered: worker-1 at http://192.168.1.101:8081
Worker registered: worker-2 at http://192.168.1.102:8082

=== Worker Status ===
Worker ID: worker-1
Address: http://192.168.1.101:8081
Status: ACTIVE

Worker ID: worker-2
Address: http://192.168.1.102:8082
Status: ACTIVE
```

Test with:
```sql
SQL> status
SQL> CREATE DATABASE testdb;
SQL> USE testdb;
SQL> CREATE TABLE users (id INT, name VARCHAR(50));
```

## Running as System Services (Linux)

### Worker Service

Create `/etc/systemd/system/db-worker.service`:
```ini
[Unit]
Description=Distributed Database Worker
After=network.target

[Service]
Type=simple
User=dbuser
WorkingDirectory=/home/dbuser/Worker
ExecStart=/usr/bin/java -jar /home/dbuser/Worker/build/libs/Worker-1.0-SNAPSHOT.jar worker-1 8081
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable db-worker
sudo systemctl start db-worker
sudo systemctl status db-worker
```

### Coordinator Service

Create `/etc/systemd/system/db-coordinator.service`:
```ini
[Unit]
Description=Distributed Database Coordinator
After=network.target

[Service]
Type=simple
User=dbuser
WorkingDirectory=/home/dbuser/Coordinator
ExecStart=/usr/bin/java -jar /home/dbuser/Coordinator/build/libs/Coordinator-1.0-SNAPSHOT.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable db-coordinator
sudo systemctl start db-coordinator
sudo systemctl status db-coordinator
```

## Monitoring

### Check Worker Health
```bash
# From any machine
curl http://192.168.1.101:8081/health
curl http://192.168.1.102:8082/health
```

### View Logs
```bash
# If running as service
sudo journalctl -u db-worker -f
sudo journalctl -u db-coordinator -f

# If running in terminal
# Logs appear in console
```

### Monitor Network
```bash
# Check listening ports
netstat -tuln | grep 808

# Check connections
netstat -an | grep ESTABLISHED
```

## Troubleshooting

### Workers Not Connecting
1. **Ping test**: `ping 192.168.1.101`
2. **Port test**: `telnet 192.168.1.101 8081`
3. **Firewall**: `sudo ufw status`
4. **Worker running**: `ps aux | grep Worker`

### Connection Timeout
- Check network latency: `ping -c 10 192.168.1.101`
- Verify no packet loss
- Check worker CPU/memory usage

### Worker Marked Inactive
- Worker process crashed - check logs
- Network issue - verify connectivity
- Worker overloaded - check resources

## Security Considerations

### Firewall Rules
```bash
# Worker VMs - only allow from Coordinator
sudo ufw allow from 192.168.1.100 to any port 8081
sudo ufw deny 8081

# Or allow from specific subnet
sudo ufw allow from 192.168.1.0/24 to any port 8081
```

### Network Isolation
- Use private network/VLAN for database traffic
- Don't expose worker ports to public internet
- Use VPN for remote access

### Authentication (Future Enhancement)
- Add API key authentication
- Implement TLS/SSL for encrypted communication
- Use mutual TLS for worker-coordinator auth

## Performance Tuning

### JVM Options
```bash
# Worker with 4GB RAM
java -Xmx3G -Xms1G -XX:+UseG1GC -jar Worker.jar

# Coordinator with 2GB RAM
java -Xmx1536M -Xms512M -XX:+UseG1GC -jar Coordinator.jar
```

### Network Optimization
- Increase timeout for slow networks
- Adjust thread pool sizes
- Enable connection pooling

## Backup and Recovery

### Data Backup
```bash
# On each worker VM
tar -czf worker-data-backup-$(date +%Y%m%d).tar.gz data/
```

### Configuration Backup
```bash
# Backup configs
cp workers.config workers.config.backup
cp worker.config worker.config.backup
```

## Scaling

### Adding More Workers
1. Deploy new worker on new VM
2. Add entry to `workers.config`
3. Restart coordinator (or implement hot-reload)

### Load Balancing
- Current: Broadcast to all workers
- Future: Implement sharding/partitioning
- Future: Query routing based on data location
