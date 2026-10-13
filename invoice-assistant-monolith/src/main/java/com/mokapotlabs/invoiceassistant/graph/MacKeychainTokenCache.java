package com.mokapotlabs.invoiceassistant.graph;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Uses native Keychain APIs; cache values never appear in child-process arguments or files. */
public final class MacKeychainTokenCache implements SecureTokenCache {
    private static final int NOT_FOUND = -25300;
    private final byte[] service;
    private final byte[] account;
    private final Security security;

    public MacKeychainTokenCache(String clientId) {
        if (!Platform.isMac())
            throw new IllegalStateException("Protected Outlook token persistence currently requires macOS Keychain.");
        service = "invoice-assistant.graph.msal".getBytes(StandardCharsets.UTF_8);
        account = clientId.getBytes(StandardCharsets.UTF_8);
        security = Native.load("/System/Library/Frameworks/Security.framework/Security", Security.class);
    }

    public interface Security extends Library {
        int SecKeychainFindGenericPassword(
                Pointer keychain,
                int serviceLength,
                byte[] service,
                int accountLength,
                byte[] account,
                IntByReference length,
                PointerByReference password,
                PointerByReference item);

        int SecKeychainAddGenericPassword(
                Pointer keychain,
                int serviceLength,
                byte[] service,
                int accountLength,
                byte[] account,
                int length,
                byte[] password,
                PointerByReference item);

        int SecKeychainItemModifyAttributesAndData(Pointer item, Pointer attributes, int length, byte[] data);

        int SecKeychainItemFreeContent(Pointer attributes, Pointer data);

        int SecKeychainItemDelete(Pointer item);
    }

    public interface CoreFoundation extends Library {
        void CFRelease(Pointer object);
    }

    private static final Pointer DEFAULT = Pointer.NULL; // Native APIs explicitly define NULL as the default keychain.

    @Override
    public synchronized Optional<String> read() {
        var length = new IntByReference();
        var data = new PointerByReference();
        var item = new PointerByReference();
        int code = security.SecKeychainFindGenericPassword(
                DEFAULT, service.length, service, account.length, account, length, data, item);
        if (code == NOT_FOUND) return Optional.empty();
        requireSuccess(code);
        byte[] copy = data.getValue().getByteArray(0, length.getValue());
        try {
            return Optional.of(new String(copy, StandardCharsets.UTF_8));
        } finally {
            Arrays.fill(copy, (byte) 0);
            security.SecKeychainItemFreeContent(DEFAULT, data.getValue());
            release(item.getValue());
        }
    }

    @Override
    public synchronized void write(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        var length = new IntByReference();
        var data = new PointerByReference();
        var item = new PointerByReference();
        try {
            int code = security.SecKeychainFindGenericPassword(
                    DEFAULT, service.length, service, account.length, account, length, data, item);
            if (code == NOT_FOUND) {
                requireSuccess(security.SecKeychainAddGenericPassword(
                        DEFAULT, service.length, service, account.length, account, bytes.length, bytes, item));
            } else {
                requireSuccess(code);
                security.SecKeychainItemFreeContent(DEFAULT, data.getValue());
                requireSuccess(
                        security.SecKeychainItemModifyAttributesAndData(item.getValue(), DEFAULT, bytes.length, bytes));
            }
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (item.getValue() != null) release(item.getValue());
        }
    }

    @Override
    public synchronized void clear() {
        var length = new IntByReference();
        var data = new PointerByReference();
        var item = new PointerByReference();
        int code = security.SecKeychainFindGenericPassword(
                DEFAULT, service.length, service, account.length, account, length, data, item);
        if (code == NOT_FOUND) return;
        requireSuccess(code);
        try {
            security.SecKeychainItemFreeContent(DEFAULT, data.getValue());
            requireSuccess(security.SecKeychainItemDelete(item.getValue()));
        } finally {
            release(item.getValue());
        }
    }

    private static void release(Pointer item) {
        Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", CoreFoundation.class)
                .CFRelease(item);
    }

    private static void requireSuccess(int code) {
        if (code != 0)
            throw new IllegalStateException(
                    "macOS Keychain is unavailable or access was denied (status " + code + ").");
    }
}
