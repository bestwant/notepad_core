import 'dart:typed_data';

import 'package:convert/convert.dart';
import 'package:flutter/foundation.dart';

import '../Notepad.dart';

const SUFFIX = 'ba5e-f4ee-5ca1-eb1e5e4b1ce0';

const SERV__COMMAND = '57444d01-$SUFFIX';
const CHAR__COMMAND_REQUEST = '57444e02-$SUFFIX';
const CHAR__COMMAND_RESPONSE = CHAR__COMMAND_REQUEST;

const SERV__SYNC = '57444d06-$SUFFIX';
const CHAR__SYNC_INPUT = '57444d07-$SUFFIX';

const SERV__FILE_INPUT = '57444d03-$SUFFIX';
const CHAR__FILE_INPUT_CONTROL_REQUEST = '57444d04-$SUFFIX';
const CHAR__FILE_INPUT_CONTROL_RESPONSE = CHAR__FILE_INPUT_CONTROL_REQUEST;
const CHAR__FILE_INPUT = '57444d05-$SUFFIX';

const SERV__FILE_OUTPUT = '01ff5550-$SUFFIX';
const CHAR__FILE_OUTPUT_CONTROL_REQUEST = '01ff5551-$SUFFIX';
const CHAR__FILE_OUTPUT_CONTROL_RESPONSE = CHAR__FILE_OUTPUT_CONTROL_REQUEST;
const CHAR__FILE_OUTPUT = '01ff5552-$SUFFIX';

final defaultAuthToken = Uint8List.fromList([0x00, 0x00, 0x00, 0x01]);

class WoodemiCommand<T> extends NotepadCommand<T> {
  WoodemiCommand({
    @required Uint8List request,
    Predicate intercept,
    Handle<T> handle,
  }) : super(
    request: request,
    intercept: intercept ?? defaultIntercept(request),
    handle: handle ?? defaultHandle,
  );

  static Predicate defaultIntercept(Uint8List request) {
    return (value) => value[0] == 0x07 && value[1] == request.first;
  }

  static final Handle<void> defaultHandle = (response) {
    if (response[4] != 0x00) throw Exception('WOODEMI_COMMAND fail: response ${hex.encode(response)}');
  };
}