# Validation

- Compiled against Android SDK 35 with Java 8 bytecode; D8 minimum API 26.
- Built resource table and Android manifest with AAPT2.
- Passed independent speed-model checks for conversion, freshness, accuracy filtering, invalid values, sample ordering, and reset.
- Verified APK alignment and APK Signature Scheme v2/v3 signatures.
- Inspected final APK manifest: foreground fine/coarse location only; no Internet or background location permission.

Live GPS reception and accuracy require a physical phone and have not been road-tested. The speedometer displays unavailable speed as a dash after four seconds without an accepted GPS speed fix.
