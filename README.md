# Suspicious Transaction Detection System

A comprehensive, containerized system for detecting suspicious financial transactions using ETL pipelines, machine learning scoring, and real-time monitoring.

## 🚀 Overview

This project implements a robust suspicious transaction detection system that processes financial data from multiple banks, enriches transaction information, and applies risk scoring algorithms to identify potentially fraudulent activities. The system is built with modern technologies including Scala (ZIO), Python (FastAPI), and containerized services.

## 🏗️ Architecture

The system consists of four main components:

- **ETL Pipeline** - Scala-based data processing pipeline using ZIO
- **Hermes Service** - Python FastAPI service for transaction enrichment and scoring
- **MinIO** - Object storage for transaction data files
- **PostgreSQL** - Relational database for storing processed transactions and audit logs

```
MinIO Storage > ETL Pipeline > Hermes Service > PostgreSQL Database
```

## ✨ Features

- **Multi-format Support**: Processes both JSON and CSV transaction files
- **Real-time Enrichment**: Enhances transaction data with merchant categorization
- **Risk Scoring**: Calculates suspicion scores based on amount, category, and timing
- **Audit Trail**: Comprehensive logging and tracking of all data processing
- **Scalable Architecture**: Containerized services with health checks
- **Multi-bank Support**: Handles transactions from multiple financial institutions

## 🛠️ Technology Stack

### Backend Services
- **Scala 2.13.13** with **ZIO 2.0** for functional programming and concurrency
- **Python 3.x** with **FastAPI** for RESTful API services
- **PostgreSQL 16** for data persistence
- **MinIO** for S3-compatible object storage

### Key Libraries
- **ZIO**: Functional effects and concurrency
- **ZIO Config**: Configuration management
- **ZIO Logging**: Structured logging
- **Circe**: JSON parsing and serialization
- **STTP**: HTTP client for API communication
- **HikariCP**: Database connection pooling

## 🚀 Quick Start

### Prerequisites
- Docker and Docker Compose
- Java 8+ (for Scala compilation)
- Python 3.8+ (for local development)

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/Suspicious-Transaction-Detection.git
cd Suspicious-Transaction-Detection
```

### 2. Set Environment Variables
Create a `.env` file in the root directory:
```bash
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
```

### 3. Start the Services
```bash
docker-compose up -d
```

This will start:
- **MinIO**: http://localhost:9000 (API) / http://localhost:9001 (Console)
- **PostgreSQL**: localhost:5432
- **Hermes Service**: http://localhost:8000
- **ETL Pipeline**: Built and ready to run

### 4. Access Services
- **MinIO Console**: http://localhost:9001 (login: minioadmin/minioadmin)
- **Hermes API**: http://localhost:8000/docs (Swagger UI)
- **PostgreSQL**: localhost:5432 (etl_user/etl_password)

## 📁 Project Structure

```
├── docker-compose.yml          # Service orchestration
├── etl-pipeline/              # Scala ETL application
│   ├── build.sbt             # SBT build configuration
│   ├── Dockerfile            # ETL service container
│   └── src/main/scala/etl/   # Source code
│       ├── Main.scala        # Application entry point
│       ├── FileProcessor.scala # File processing logic
│       ├── MinioService.scala # MinIO integration
│       ├── models.scala       # Data models
│       └── ...               # Other components
├── hermes/                    # Python enrichment service
│   ├── hermes_service.py     # FastAPI application
│   ├── requirements.txt      # Python dependencies
│   └── Dockerfile           # Service container
├── minio/                     # Object storage data
│   └── data/transactions/    # Transaction files by bank/date
├── postgres/                  # Database initialization
│   └── init.sql              # Database schema
└── README.md                  # This file
```

## 🔧 Configuration

### ETL Pipeline Configuration
The ETL pipeline can be configured through environment variables:

```bash
MINIO_ENDPOINT=http://minio:9000
MINIO_BUCKET=transactions
MINIO_ACCESSKEY=minioadmin
MINIO_SECRETKEY=minioadmin
HERMES_URL=http://hermes:8000
POSTGRES_URL=jdbc:postgresql://postgres:5432/etl_db
POSTGRES_DATABASE=etl_db
POSTGRES_USER=etl_user
POSTGRES_PASSWORD=etl_password
```

### MinIO Configuration
- **API Port**: 9000
- **Console Port**: 9001
- **Default Credentials**: minioadmin/minioadmin

## 📊 Data Flow

1. **Data Ingestion**: Transaction files are uploaded to MinIO storage
2. **ETL Processing**: Scala pipeline processes files and extracts transactions
3. **Enrichment**: Hermes service categorizes merchants and converts currencies
4. **Risk Scoring**: Suspicion scores are calculated based on multiple factors
5. **Storage**: Processed data is stored in PostgreSQL with audit trails

## 🎯 API Endpoints

### Hermes Service

#### Enrich Transaction
```http
GET /enrich?txn_id={id}&amount={amount}&currency={currency}&merchant={merchant}
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "txn_id": "TXN001",
    "amount_sar": 375.0,
    "category": "Electronics"
  }
}
```

#### Calculate Risk Score
```http
GET /score?txn_id={id}&amount_sar={amount}&category={category}&timestamp={timestamp}
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "txn_id": "TXN001",
    "suspicion_score": 45
  }
}
```

## 🧪 Development

### Building the ETL Pipeline
```bash
cd etl-pipeline
sbt compile
sbt assembly
```

### Running Tests
```bash
cd etl-pipeline
sbt test
```

### Local Development
```bash
# Start only dependencies
docker-compose up -d minio postgres

# Run ETL pipeline locally
cd etl-pipeline
sbt run

# Run Hermes service locally
cd hermes
pip install -r requirements.txt
uvicorn hermes_service:app --reload --host 0.0.0.0 --port 8000
```

## 📈 Monitoring and Health Checks

All services include health checks:
- **MinIO**: Health endpoint at `/minio/health/live`
- **PostgreSQL**: Connection readiness check
- **Hermes**: FastAPI built-in health monitoring
- **ETL Pipeline**: Comprehensive logging and audit trails


## 🤝 MinIO Sample Files

Sample Files are available in  "Sample Files" folder in addition to a data generation code.


---
