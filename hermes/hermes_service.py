# hermes_service.py
from fastapi import FastAPI
from typing import Optional
from datetime import datetime, timezone

app = FastAPI(title="Hermes API", version="1.0")

@app.get("/enrich")
def enrich(txn_id: str, merchant: str, amount: float, currency: str):
    """
    Enrich transaction data with additional information.

    Args:
        txn_id (str): The transaction ID.
        merchant (str): The merchant name.
        amount (float): The transaction amount.
        currency (str): The transaction currency.

    Returns:
        dict: A dictionary containing enriched transaction data.
    """

    categorization_table = {    # Mock categorization table
        "Electronics": ["Extra", "Jarir", "Sony"],
        "Groceries": ["Panda", "AlOthaim", "Tamimi"],
        "Clothing": ["Zara", "H&M", "Nike"],
        "E-commerce": ["Amazon", "SHEIN", "AliExpress"],
        "Jewelry": ["goldenPalace", "AlFardan", "Tiffany"]
    }
    
    currency = currency.upper()

    currency_table_to_sar = {   # Mock currency conversion rates to SAR
        "USD": 3.75,
        "EUR": 4.35,
        "GBP": 5.0,
        "SAR": 1.0,
    }
    
    data = {
        "txn_id": txn_id,
        "amount_sar": amount * currency_table_to_sar.get(currency, 1),
        "category": next((category for category, keywords in categorization_table.items() if merchant in keywords), "Unknown")
    }
    return {"status": "success", "data": data}

@app.get("/score")
def score(txn_id: str, merchant: str, amount_sar: float, currency: str, category: str, timestamp: str):
    """
    Calculate a suspicion score for a transaction based on various factors.
    
    Args:
        txn_id (str): The transaction ID.
        merchant (str): The merchant name.
        amount_sar (float): The transaction amount in SAR.
        currency (str): The transaction currency.
        category (str): The transaction category.
        timestamp (str): The transaction timestamp in ISO format.

    """

    formatted_timestamp = datetime.fromisoformat(timestamp.replace("Z", "+00:00"))

    suspicion_score = calculate_suspicion_score(amount_sar, category, formatted_timestamp.hour)
    return {
        "status": "success",
        "data": {
            "txn_id": txn_id,
            "suspicion_score": suspicion_score
        }
    }

def calculate_suspicion_score(amount_sar: float, category: str, transaction_hour: int) -> int:
    score = 0
    
    # Amount factor
    if amount_sar > 1000:
        score += 30
    elif amount_sar > 500:
        score += 20
    elif amount_sar > 100:
        score += 10

    # Category factor
    risky_categories = ["Electronics", "Jewelry"]
    medium_risk_categories = ["E-commerce"]

    if category in risky_categories:
        score += 25
    elif category in medium_risk_categories:
        score += 15

    # Time factor
    if transaction_hour < 6 or transaction_hour > 23:
        score += 20

    return score
