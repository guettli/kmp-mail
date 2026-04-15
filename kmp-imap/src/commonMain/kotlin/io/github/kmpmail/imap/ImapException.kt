package io.github.kmpmail.imap

class ImapException(message: String) : Exception(message)

class ImapNoException(val command: String, message: String) :
    Exception("IMAP NO [$command]: $message")

class ImapBadException(val command: String, message: String) :
    Exception("IMAP BAD [$command]: $message")
