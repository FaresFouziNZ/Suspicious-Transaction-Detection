# hermes_service.py
from fastapi import FastAPI
from typing import Optional

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