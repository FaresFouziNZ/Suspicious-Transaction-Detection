import os
import json
import csv
import random
from datetime import datetime, timedelta

# Configuration
banks = ["bank_A", "bank_B", "bank_C"]
output_dir = "data"
start_date = datetime(2025, 6, 25)
days = 7   # how many days of data
batches_per_day = 3
transactions_per_batch = 5

merchants = ["Amazon", "Walmart", "Noon", "eBay", "Apple Store", "Carrefour"]
currencies = ["USD", "EUR", "SAR", "GBP"]

# Bank-specific CSV formats
bank_csv_formats = {
    "bank_A": "fixed",    # always same order, no extra fields
    "bank_B": "random",   # shuffle each time, sometimes add random extras
    "bank_C": "custom"    # custom fixed order, always adds extra fields
}

# Custom fixed order for specific banks
custom_formats = {
    "bank_C": ["user_id", "txn_id", "merchant", "amount", "currency", "timestamp"]
}

# Extra fields simulation
extra_fields_bank = {
    "bank_A": [],  # no extra fields
    "bank_B": ["location", "branch_code"],  # optional random subset
    "bank_C": ["location", "branch_code", "channel"]  # always present
}

def random_txn(txn_id, bank):
    txn = {
        "txn_id": f"TXN{txn_id}",
        "user_id": f"U{random.randint(100, 999)}",
        "amount": round(random.uniform(10.0, 1000.0), 2),
        "currency": random.choice(currencies),
        "timestamp": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        "merchant": random.choice(merchants)
    }

    # Add extra fields depending on bank
    extras = extra_fields_bank.get(bank, [])
    if bank == "bank_B":
        # Randomly choose some extras for each transaction
        chosen = random.sample(extras, random.randint(0, len(extras)))
        for field in chosen:
            txn[field] = f"{field.upper()}_{random.randint(1, 50)}"
    elif bank == "bank_C":
        # Always include all extras
        for field in extras:
            txn[field] = f"{field.upper()}_{random.randint(1, 50)}"

    return txn

def write_json(filepath, txns):
    with open(filepath, "w") as f:
        json.dump(txns, f, indent=2)

def write_csv(filepath, txns, bank):
    # Gather all possible fields across all txns
    all_keys = set()
    for t in txns:
        all_keys.update(t.keys())

    format_type = bank_csv_formats.get(bank, "fixed")

    if format_type == "fixed":
        fieldnames = list(all_keys)
    elif format_type == "random":
        fieldnames = list(all_keys)
        random.shuffle(fieldnames)
    elif format_type == "custom":
        base_order = custom_formats[bank]
        extras = [f for f in all_keys if f not in base_order]
        fieldnames = base_order + extras
    else:
        fieldnames = list(all_keys)

    with open(filepath, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(txns)


# Generate data
txn_counter = 1000
for bank in banks:
    for d in range(days):
        date_str = (start_date + timedelta(days=d)).strftime("%Y-%m-%d")
        day_dir = os.path.join(output_dir, bank, date_str)
        os.makedirs(day_dir, exist_ok=True)

        for b in range(1, batches_per_day + 1):
            txns = [random_txn(txn_counter + i, bank) for i in range(transactions_per_batch)]
            txn_counter += transactions_per_batch

            if b % 2 == 1:
                filepath = os.path.join(day_dir, f"txn_batch_{b:03d}.json")
                write_json(filepath, txns)
            else:
                filepath = os.path.join(day_dir, f"txn_batch_{b:03d}.csv")
                write_csv(filepath, txns, bank)

print(f"✅ Generated sample data under '{output_dir}/'")
