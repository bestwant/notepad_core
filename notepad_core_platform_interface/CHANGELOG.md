## [1.4.2] - 2020-03-05
- Add `services` to `NotepadCorePlatform.connect`

## [1.3.0] - 2019-12-23

### NotepadCorePlatform

- Add `requestMtu`
- Append `writeValue` with optional `BleOutputProperty`
- Add `requestConnectionPriority` & `BleConnectionPriority`

## [1.2.0] - 2019-12-21

### NotepadCorePlatform

- Add `isBluetoothAvailable`
- Notify `BluetoothState` through `messageHandler`
- Add `BleInputProperty` to `setNotifiable`

## [1.1.0] - 2019-12-20

### NotepadCorePlatform

#### Operate service/characteristic

- `readValue`
- `inputValueStream`
- Add `NotapadConnectionState.awaitConfirm`

## [1.0.0] - 2019-12-19

### NotepadCorePlatform

#### Scan/Request device
- `requestDevice` for Web
- `startScan`, `stopScan`, `scanResultStream` for Android/iOS

#### Connect/Disconnect device
- `connect`, `disconnect`
- `messageHandler` for connection state change event

#### Operate service/characteristic
- `setNotifiable`
- `writeValue`