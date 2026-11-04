# CANboat message-definition migration (MSG-422)

The original migration adopted CANboat 9.1.0 / schema 2.8.0 NMEA 2000 and J1939 XML as separate registries. The bundled NMEA 2000 catalogue is now CANboat 9.2.0; J1939 remains at the original 9.1.0 baseline. Canonical names are used, and J1939 transport remains excluded.

## Review and design

The old parser loses variant discriminators, fallback and repeat metadata and maps unknown field types to NUMBER. Retain complete modern schema metadata and use a schema-aware codec for modern databases. Preserve the existing legacy XML path for explicitly supplied legacy files; change the NMEA default to canboat.xml and expose a separate J1939 factory. Keep message codecs independent of transport. Unsupported wire transports remain rejected.

A single merged PGN map is unsuitable because protocol definitions overlap. Converting the maintained XML to the old subset is unsuitable because it loses the same features. The implementation therefore uses separate protocol registries and one shared modern schema engine, preserving all variants and generated metadata.

CANboat names are intentional external output changes. Existing envelope shape and numeric lookup representation remain unchanged. Match conditions select a specific layout; fallback is lower priority. Repeating sets become arrays of objects keyed as list/list2, preserving canonical field IDs within each row. Malformed/truncated dynamic layouts fail explicitly. Binary data uses base64. Unknown PGNs retain the existing null/raw handling.

## Implementation plan

- [x] Add full-schema models/parser and version/protocol validation; bundle both pinned files, notices and source provenance.
- [x] Retain all variants in the registry; select by payload discriminators or explicit message name on encoding; preserve transport metadata and detect ambiguity.
- [x] Implement schema-aware scalar, float, string, binary, variable and repeated field codecs, including dynamic lookup-controlled fields. Preserve the explicitly loaded legacy decoder.
- [x] Default NMEA loading to canboat.xml; expose independent J1939 message registry loading without adding transport.
- [x] Update server outbound/state names and tests on the corresponding feature branch.
- [x] Validate real vectors, full definition counts, malformed boundaries, variants and round trips; run available module/server suites and report Maven limitations.
- [x] Independently review the complete change, resolve findings, commit and push feature branches (publication recorded in MSG-422).

Review focus: unmatched/ambiguous variants, truncated repeats, fields following variable strings/repeats, unsigned 64-bit values, unsupported type/version errors. Add focused tests before implementation for these boundaries.

## Public API and compatibility

```java
N2kMessageParser nmea = new N2kMessageParser(N2kParserFactory.getN2kParser());
N2kMessageParser j1939 = new N2kMessageParser(N2kParserFactory.getJ1939Parser());
```

The existing XML parser/compiler API also accepts explicitly supplied modern or legacy files. The default NMEA catalogue contains 640 definitions across 348 PGNs; J1939 contains 89 definitions across 84 PGNs. Registries remain separate because PGN numbers overlap. All variants remain accessible through `getVariants(pgn)`.

Canonical message IDs and field IDs replace the old output names. Unavailable numeric values are JSON null, ISO NAME retains exact unsigned 64-bit precision, lookup values remain numeric, binary values are base64, and repeated records appear in `list` and `list2`. The envelope remains `packet` plus `name`. Legacy XML is retained for callers explicitly selecting that resource. Server outbound providers and state listeners are updated on the matching server branch; listeners accept their former input aliases, preferring canonical values.

For unknown manufacturers on known PGNs, decoding uses the lower-priority range fallback and preserves raw bytes. Explicit conflicting encode names fail. Unknown field types and unsupported schema major versions fail at load time. J1939 BAM/RTS/CTS and CAN FD are outside this change; this factory supplies message definitions and payload codecs only.

## Sources and verification

Current bundled sources from `canboat/canboat`:

| Catalogue | Upstream file | Version | Source commit | Schema |
| --- | --- | --- | --- | --- |
| NMEA 2000 | `docs/canboat.xml` | [9.2.0](https://github.com/canboat/canboat/releases/tag/v9.2.0) | `5add9cefe5711d3d3ab600ea4b37caa95a17f2f6` | 2.8.0 |
| J1939 | `docs/canboat-j1939.xml` | 9.1.0 | `5b8340403e009b935f6a398b1ef0244a03e3d128` | 2.8.0 |

Embedded upstream notices are preserved. RDS G0 mappings in `CanboatText` are attributed to CANboat's Apache-licensed charset source.

### NMEA 2000 update to 9.2.0

[CANbus issue #24](https://github.com/Maps-Messaging/canbus_interface/issues/24) is implemented in [commit 832aa1b](https://github.com/Maps-Messaging/canbus_interface/commit/832aa1ba93a28bff8d51a816dd6f55c78b7cd1a2). Only `canbus-nmea2000/src/main/resources/n2k/canboat.xml` was replaced, byte-for-byte, with the upstream release file (Git blob `ef785cf008cdd9e3720540e8c929b1a34dbf269a`). It retains 640 definitions across 348 PGNs and schema 2.8.0. The J1939 resources and decoder implementation were not changed.

The catalogue update includes heave delay fields, BEP CZone output-status fields, the Furuno satellite-azimuth signedness correction, and revised proprietary-field layouts and lookup names. Consumers of affected decoded messages should review the [upstream release notes](https://github.com/canboat/canboat/releases/tag/v9.2.0) for field changes. Upstream CANboat executable fixes are not Java codec changes supplied by this XML replacement.

Validation with JDK 25: `mvn -pl canbus-nmea2000 -am test '-Dtest=Canboat*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dgpg.skip=true -Ddependency-check.skip=true` passed 899 focused tests with zero failures, errors or skips. XML parsing, catalogue counts and the upstream Git blob identity were also checked. The broader reactor test attempt stopped in `canbus-core` because Mockito's agent could not initialise in the test environment. Full-suite and hardware interoperability validation are not established by this update.

The existing wire-vector corpus remains pinned to CANboat 9.1.0 commit `5b8340403e009b935f6a398b1ef0244a03e3d128`; it was not regenerated as part of this catalogue update.

### Original 9.1.0 migration verification

Verification uses JDK 25 with `javac --release 21` and JUnit Platform. Tests cover every non-fallback definition, independent upstream wire vectors, discriminator selection, both repeat groups, dynamic values, referenced fields, character encodings, sentinels, unsigned values, typed schemas, malformed payloads and the existing legacy/capture regressions. An independent review found and resolved fallback re-encoding, string lengths/encodings, dynamic length/sentinel handling, bit-field padding and partial-repeat handling.

The additional 10,000-record canonical capture probe decoded 9,501 records with zero value mismatches after normalizing absent versus null fields; 499 remained unrecognized. Older short records can gain trailing unavailable fields when re-encoded using modern definitions. This is a sample check, not certification of every upstream definition or hardware interoperability.

Full Maven execution was attempted for the NMEA reactor and focused server tests. It is blocked in this environment by dependency resolution (JaCoCo 0.8.14, the private license-manager parent and other unavailable artifacts / repository DNS). Direct Java compilation and JUnit execution passed: 1,028 library tests, 101 focused server tests, 81 broader server listener/source tests (overlapping suites), and 52 protocol routing/branch tests; a normal Maven/CI build is still required in the deployment environment. Build/install the canbus branch before building the server branch so both use the updated snapshot library.
