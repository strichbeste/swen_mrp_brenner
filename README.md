# Media Ratings Platform (MRP)

## Setup

### 1. PostgreSQL starten
```bash
docker run --name mrp-db -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=mrp -p 5432:5432 -d postgres
```

### 2. Schema anlegen
```bash
psql -h localhost -U postgres -d mrp -f schema.sql
```

### 3. Projekt bauen
```bash
mvn clean package -DskipTests
```

### 4. Starten
```bash
java -jar target/mrp-1.0-jar-with-dependencies.jar
```

### 5. Tests ausfuehren
```bash
mvn test
```

## Token-Nutzung
Nach dem Login den Token als Bearer Header mitsenden:
```
Authorization: Bearer username-mrpToken
```

## GitHub
https://github.com/strichbeste/swen_mrp_brenner
