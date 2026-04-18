from app.services.scanner_service import ScannerService


def _eval(patterns):
    return {"detected_patterns": patterns}


def test_hash_is_stable_for_same_inputs():
    h1 = ScannerService._build_signal_hash("RELIANCE", 2800.00, 2740.00, _eval(["Bullish Engulfing"]))
    h2 = ScannerService._build_signal_hash("RELIANCE", 2800.00, 2740.00, _eval(["Bullish Engulfing"]))
    assert h1 == h2
    assert len(h1) == 64  # SHA-256 hex


def test_hash_changes_when_entry_changes():
    h1 = ScannerService._build_signal_hash("RELIANCE", 2800.00, 2740.00, _eval([]))
    h2 = ScannerService._build_signal_hash("RELIANCE", 2801.00, 2740.00, _eval([]))
    assert h1 != h2


def test_hash_changes_when_patterns_change():
    h1 = ScannerService._build_signal_hash("TCS", 3700.00, 3620.00, _eval(["Bullish Engulfing"]))
    h2 = ScannerService._build_signal_hash("TCS", 3700.00, 3620.00, _eval(["Hammer"]))
    assert h1 != h2


def test_hash_is_case_sensitive_on_symbol():
    h1 = ScannerService._build_signal_hash("TCS", 100.0, 98.0, _eval([]))
    h2 = ScannerService._build_signal_hash("tcs", 100.0, 98.0, _eval([]))
    assert h1 != h2
