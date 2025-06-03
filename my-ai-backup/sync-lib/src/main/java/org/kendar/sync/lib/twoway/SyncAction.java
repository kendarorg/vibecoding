package org.kendar.sync.lib.twoway;

enum SyncAction {
    UPDATE_FROM_REMOTE,
    UPDATE_TO_REMOTE,
    DELETE_LOCAL,
    DELETE_REMOTE,
    CONFLICT,
    NO_ACTION
}
