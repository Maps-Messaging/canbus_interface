# CANbus 1.1.0 (unreleased)

This release adopts CANboat as the default NMEA 2000 message-definition and JSON naming standard, and adds a separate J1939 message-definition registry. The default JSON output changes; review the migration notes below before replacing the library in an existing integration.

## Features and improvements

### Message definitions

- Load CANboat 9.1.0 / XML schema 2.8.0 definitions from `n2k/canboat.xml` and `n2k/canboat-j1939.xml`.
- Expand NMEA 2000 coverage from 113 definitions covering 113 distinct PGNs to 640 definitions covering 348 distinct PGNs.
- Add 89 J1939 definitions covering 84 distinct PGNs. NMEA 2000 and J1939 use separate registries because their PGN numbers can overlap.
- Retain multiple definitions for a PGN. Decoding selects a layout using payload discriminator fields such as manufacturer, industry group or command; matching layouts follow CANboat definition order.
- Select an encoding variant using the envelope's `name`, or discriminator fields when they identify one variant. Reject ambiguous input and explicit names that conflict with supplied discriminators.
- Preserve raw payload bytes through available range fallbacks when no specific definition matches, including unknown manufacturers on known PGNs.

### Payload codecs and schemas

- Support repeated field sets, dynamically typed values and fields referencing another PGN's definition.
- Support IEEE 754 floating-point values, exact unsigned 64-bit ISO NAME values, numeric scaling and offsets, and raw unavailable-value sentinels.
- Support fixed and variable strings, UTF-8/Latin-1 fallback, UTF-16LE and declared RDS G0 character encoding.
- Combine continued J1939 SPN fragments into one decoded value.
- Generate JSON schemas describing named variants, typed repeat arrays, nullable fields and binary content.
- Reject unknown field types, unsupported XML schema major versions, malformed dynamic layouts and truncated repeat records explicitly.

## Compatibility and migration

**Breaking change:** the default catalogue now emits canonical CANboat message IDs and field IDs. Updating an existing application can require changes to JSON consumers, producers and any topic names derived from message IDs.

- The envelope keys remain `packet` and `name`; the value of `name` and keys within `packet` can change.
- Repeated records appear under `packet.list` and `packet.list2`, with canonical field IDs within each row. This replaces literal first/last or numbered fields in affected legacy layouts.
- Unavailable numeric values are JSON `null`. Lookup values remain numeric; binary content uses base64.
- Older, shorter payloads can gain trailing unavailable fields when re-encoded using the modern definition.
- Explicitly selecting `n2k/NMEA_database_1_300.xml` retains the legacy parser/codec path. The default resource is `n2k/canboat.xml`.
- Obtain the independent J1939 registry through `N2kParserFactory.getJ1939Parser()`; `getN2kParser()` supplies the NMEA 2000 registry. The existing XML parser/compiler API also accepts explicitly supplied definition files.
- This J1939 addition covers message definitions and payload codecs. BAM/RTS/CTS transport and CAN FD are outside this release's CANboat migration scope.
- MapsMessaging server integrations must use the corresponding canonical-field provider and listener changes. The updated state listeners accept their former input aliases, preferring canonical keys; this is not a global legacy-JSON compatibility layer.

### Existing message-name changes

These are the 16 changed message IDs among the previously supported PGNs. Other new variants and new PGNs add names without a direct predecessor.

| PGN | Previous `name` | CANboat `name` |
|---|---|---|
| 127237 | `headingtrackControl` | `headingTrackControl` |
| 128259 | `speedWaterReferenced` | `speed` |
| 129027 | `positionDeltaHighPrecisionRapidUpdate` | `positionDeltaRapidUpdate` |
| 129028 | `altitudeDeltaHighPrecisionRapidUpdate` | `altitudeDeltaRapidUpdate` |
| 129045 | `userDatumSettings` | `userDatum` |
| 129285 | `navigationRoutewpInformation` | `navigationRouteWpInformation` |
| 129301 | `timeTofromMark` | `navigationRouteTimeToFromMark` |
| 129799 | `radioFrequencymodepower` | `radioFrequencyModePower` |
| 129800 | `aisUtcdateInquiry` | `aisUtcDateInquiry` |
| 129809 | `aisClassBCsStaticDataReportPartA` | `aisClassBStaticDataMsg24PartA` |
| 129810 | `aisClassBCsStaticDataReportPartB` | `aisClassBStaticDataMsg24PartB` |
| 130052 | `lorancTdData` | `loranCTdData` |
| 130053 | `lorancRangeData` | `loranCRangeData` |
| 130054 | `lorancSignalData` | `loranCSignalData` |
| 130066 | `routeAndWpServiceRoutewplistAttributes` | `routeAndWpServiceRouteWpListAttributes` |
| 130310 | `environmentalParameters` | `environmentalParametersObsolete` |

### Field-name changes used by server integrations

The following mappings cover request, product/configuration, state-listener and AIS provider fields used by the server. Other PGNs also have field-name or repeat-layout changes; this table is not a complete catalogue diff. Mappings are PGN-specific and case-sensitive, not global substitutions.

| PGN | Previous field | CANboat field |
|---|---|---|
| 59904 | `requestedPgn` | `pgn` |
| 126996 | `nmea2000DatabaseVersion` | `nmea2000Version` |
| 126996 | `nmeaManufacturersProductCode` | `productCode` |
| 126996 | `manufacturersModelId` | `modelId` |
| 126996 | `manufacturersSoftwareVersionCode` | `softwareVersionCode` |
| 126996 | `manufacturersModelVersion` | `modelVersion` |
| 126996 | `manufacturersModelSerialCode` | `modelSerialCode` |
| 126996 | `nmea2000CertificationLevel` | `certificationLevel` |
| 126998 | `installationDescriptionField1` | `installationDescription1` |
| 126998 | `installationDescriptionField2` | `installationDescription2` |
| 126998 | `manufacturerInformationField3` | `manufacturerInformation` |
| 127250 | `headingSensorReading` | `heading` |
| 127250 | `headingSensorReference` | `reference` |
| 127251 | `sequenceId` | `sid` |
| 127251 | `rateOfTurn` | `rate` |
| 127258 | `sequenceId` | `sid` |
| 127258 | `variationSource` | `source` |
| 127258 | `ageOfServiceDate` | `ageOfService` |
| 127508 | `batteryInstance` | `instance` |
| 127508 | `batteryVoltage` | `voltage` |
| 127508 | `batteryCurrent` | `current` |
| 127508 | `batteryCaseTemperature` | `temperature` |
| 127509 | `inverterInstance` | `instance` |
| 127509 | `inverterEnabledisable` | `inverterEnable` |
| 129026 | `courseOverGround` | `cog` |
| 129026 | `speedOverGround` | `sog` |
| 129039 | `raimflag` | `raim` |
| 129039 | `trueHeading` | `heading` |
| 129039 | `classBUnitFlag` | `unitType` |
| 129039 | `classBDisplayFlag` | `integratedDisplay` |
| 129039 | `classBDscFlag` | `dsc` |
| 129039 | `classBBandFlag` | `band` |
| 129039 | `classBMsg22Flag` | `canHandleMsg22` |
| 129039 | `modeFlag` | `aisMode` |
| 129039 | `communicationStateSelectorFlag` | `aisCommunicationState` |
| 129040 | `raimflag` | `raim` |
| 129040 | `shipcargoType` | `typeOfShip` |
| 129040 | `shipLength` | `length` |
| 129040 | `shipBeam` | `beam` |
| 129040 | `positionReferencePointFromStarboard` | `positionReferenceFromStarboard` |
| 129040 | `positionReferencePointAftOfShipsBow` | `positionReferenceFromBow` |
| 129040 | `dataTerminalEquipmentDte` | `dte` |
| 129040 | `modeFlag` | `aisMode` |
| 129539 | `setMode` | `desiredMode` |
| 129539 | `opMode` | `actualMode` |
| 129810 | `typeOfShipAndCargo` | `typeOfShip` |
| 129810 | `callSign` | `callsign` |
| 129810 | `shipLength` | `length` |
| 129810 | `shipBeam` | `beam` |
| 129810 | `referencePointPositionFromStarboard` | `positionReferenceFromStarboard` |
| 129810 | `referencePointPositionAftOfBow` | `positionReferenceFromBow` |
| 129810 | `motherShipMmsi` | `mothershipUserId` |
| 130306 | `sequenceId` | `sid` |
| 130306 | `windDirection` | `windAngle` |
| 130306 | `windReference` | `reference` |
| 130311 | `temperatureInstance` | `temperatureSource` |
| 130311 | `humidityInstance` | `humiditySource` |

## Test coverage

- Retain the existing legacy regression tests and capture-based checks.
- Add encode/decode stability tests for every non-fallback definition in both modern catalogues.
- Add independent upstream wire vectors covering discriminator selection, floating-point values, unsigned ISO NAME values, character encodings, referenced fields, J1939 offsets and unavailable-value sentinels.
- Add focused regression tests for repeats, dynamic lengths and values, numeric overflow, fallback re-encoding, partial records and JSON schemas.
- Add matching server tests for canonical field mappings, real payload round trips and registry-based NMEA output routing.

These checks provide regression evidence; they do not certify every upstream definition or hardware interoperability.

## Definition provenance and further details

The bundled XML definitions are pinned to [CANboat commit 5b8340403e009b935f6a398b1ef0244a03e3d128](https://github.com/canboat/canboat/tree/5b8340403e009b935f6a398b1ef0244a03e3d128), version 9.1.0 / schema 2.8.0. Upstream notices are preserved, and the RDS G0 mapping is attributed in the source.

See [CANboat migration details](docs/canboat-migration.md) for implementation rationale and verification limits. Tracked under [MSG-422](https://mapsmessaging.atlassian.net/browse/MSG-422).

---

# CANbus 1.0.1

Version 1.0.1 improves reliability in CAN device access, NMEA 2000 decoding and CANaerospace handling. It also strengthens XML parsing and expands test coverage of malformed frames, edge cases and device failures.

## Fixes and improvements

### CAN device interfaces
- Close SocketCAN resources when network interface discovery fails, preventing leaked sockets during unsuccessful device initialisation.
- Improve serial CAN device shutdown and error handling, including propagation of close failures.
- Make SocketCAN interface lookup more reliable when the `ip` utility is installed in standard system directories.

### NMEA 2000
- Harden XML dialect loading against external entity declarations.
- Improve thread safety when initialising the NMEA 2000 schema cache.
- Correct candump-to-JSON output for decoded arrays and compare frame payload bytes by content.
- Improve decoding behaviour and recovery when PGNs or fields are not recognised.

### CANaerospace
- Make unknown data-type fallback handling explicit, preserving raw-frame processing when a defined type is unavailable.
- Extend validation around binary codec formats, identifier resolution and schema lookup.

## Compatibility

- This is a maintenance release; no intentional public API-breaking changes are included.
- The library continues to target Java 21.
- The Maven reactor includes `canbus-core`, `canbus-events`, `canbus-J1939` and `canbus-nmea2000`. All modules share version `1.0.1`.
- NMEA 2000 XML dialect files containing external entities are now rejected. Applications should provide self-contained, trusted schema definitions.

## Maven modules

Use the module relevant to your integration, for example:

~~~xml
<dependency>
  <groupId>io.mapsmessaging</groupId>
  <artifactId>canbus-nmea2000</artifactId>
  <version>1.1.0-SNAPSHOT</version>
</dependency>
~~~

The other published module artifact IDs are `canbus-core`, `canbus-events` and `canbus-J1939`.

## Test coverage

This release adds or expands regression tests for SocketCAN and virtual CAN interfaces, serial-device failures, CANaerospace codec boundaries and schema resolution, and NMEA 2000 parsing, string encoding and decoder recovery.
