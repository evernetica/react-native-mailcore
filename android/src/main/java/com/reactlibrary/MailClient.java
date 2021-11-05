package com.reactlibrary;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.ReadableMapKeySetIterator;

import com.libmailcore.AbstractPart;
import com.libmailcore.Attachment;
import com.libmailcore.IMAPAppendMessageOperation;
import com.libmailcore.IMAPFolderStatusOperation;
import com.libmailcore.IMAPPart;
import com.libmailcore.IMAPSearchExpression;
import com.libmailcore.IndexSet;
import com.libmailcore.Range;
import com.libmailcore.Address;
import com.libmailcore.AuthType;
import com.libmailcore.ConnectionType;
import com.libmailcore.MailException;
import com.libmailcore.MessageBuilder;
import com.libmailcore.MessageHeader;
import com.libmailcore.OperationCallback;
import com.libmailcore.SMTPOperation;
import com.libmailcore.SMTPSession;
import com.libmailcore.IMAPSession;
import com.libmailcore.IMAPOperation;
import com.libmailcore.IMAPFetchContentOperation;
import com.libmailcore.IMAPFetchFoldersOperation;
import com.libmailcore.IMAPFetchMessagesOperation;
import com.libmailcore.IMAPFetchParsedContentOperation;
import com.libmailcore.IMAPFolder;
import com.libmailcore.MessageFlag;
import com.libmailcore.IMAPSearchOperation;
import com.libmailcore.MessageParser;
import com.libmailcore.IMAPMessage;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.Map;

import android.app.Activity;
import android.content.ContentResolver;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

public class MailClient {
    private Map<String, IMAPSession> IMAPSessions = new HashMap<String, IMAPSession>();
    public SMTPSession smtpSession;
    public IMAPSession imapSession;
    public IMAPSession safeImapSession;

    public void initIMAPSession(UserCredential userCredential, final Promise promise) {
        String userName = userCredential.getUsername();
        if (!this.IMAPSessions.containsKey(userName)) {a
            final IMAPSession newImapSession = new IMAPSession();
            newImapSession.setHostname(userCredential.getHostname());
            newImapSession.setPort(userCredential.getPort());
            newImapSession.setConnectionType(ConnectionType.ConnectionTypeTLS);

            int authType = userCredential.getAuthType();
            newImapSession.setAuthType(authType);
            if (authType == AuthType.AuthTypeXOAuth2) {
                newImapSession.setOAuth2Token(userCredential.getAccessToken());
            } else {
                newImapSession.setPassword(userCredential.getPassword());
            }
            newImapSession.setUsername(userName);
            IMAPSessions.put(userName, newImapSession);

            IMAPOperation imapOperation = newImapSession.checkAccountOperation();
            imapOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    WritableMap result = Arguments.createMap();
                    result.putString("status", "SUCCESS");
                    promise.resolve(result);
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        } else {
            IMAPOperation imapOperation = this.IMAPSessions.get(userName).noopOperation();
            imapOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    WritableMap result = Arguments.createMap();
                    result.putString("status", "SUCCESS");
                    promise.resolve(result);
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        }
    }
    public void initSafeIMAPSession(UserCredential userCredential, final Promise promise) {
        safeImapSession = new IMAPSession();
        safeImapSession.setHostname(userCredential.getHostname());
        safeImapSession.setPort(userCredential.getPort());
        safeImapSession.setConnectionType(ConnectionType.ConnectionTypeTLS);

        int authType = userCredential.getAuthType();
        safeImapSession.setAuthType(authType);
        if (authType == AuthType.AuthTypeXOAuth2) {
            safeImapSession.setOAuth2Token(userCredential.getAccessToken());
        } else {
            safeImapSession.setPassword(userCredential.getPassword());
        }
        safeImapSession.setUsername(userCredential.getUsername());

        IMAPOperation imapOperation = this.safeImapSession.checkAccountOperation();
        imapOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                WritableMap result = Arguments.createMap();
                result.putString("status", "SUCCESS");
                promise.resolve(result);
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }

    public void initSMTPSession(UserCredential userCredential, final Promise promise) {
        smtpSession = new SMTPSession();
        smtpSession.setHostname(userCredential.getHostname());
        smtpSession.setPort(userCredential.getPort());
        if (userCredential.getConnectionType().equals("starttls")) {
            smtpSession.setConnectionType(ConnectionType.ConnectionTypeStartTLS);
        } else {
            smtpSession.setConnectionType(ConnectionType.ConnectionTypeTLS);
        }
        int authType = userCredential.getAuthType();
        smtpSession.setAuthType(authType);
        if (authType == AuthType.AuthTypeXOAuth2) {
            smtpSession.setOAuth2Token(userCredential.getAccessToken());
        } else {
            smtpSession.setPassword(userCredential.getPassword());
        }
        smtpSession.setUsername(userCredential.getUsername());

        SMTPOperation smtpOperation = this.smtpSession.loginOperation();
        smtpOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                WritableMap result = Arguments.createMap();
                result.putString("status", "SUCCESS");
                promise.resolve(result);
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }

    public void sendMail(final ReadableMap obj, final Promise promise, final Activity currentActivity) {
        MessageHeader messageHeader = new MessageHeader();
        if (obj.hasKey("headers")) {
            ReadableMap headerObj = obj.getMap("headers");
            ReadableMapKeySetIterator headerIterator = headerObj.keySetIterator();
            while (headerIterator.hasNextKey()) {
                String header = headerIterator.nextKey();
                String headerValue = headerObj.getString(header);
                messageHeader.setExtraHeader(header, headerValue);
            }
        }
        ReadableMap fromObj = obj.getMap("from");
        final Address fromAddress = new Address();
        fromAddress.setDisplayName(fromObj.getString("addressWithDisplayName"));
        fromAddress.setMailbox(fromObj.getString("mailbox"));
        messageHeader.setFrom(fromAddress);

        ReadableMap toObj = obj.getMap("to");
        ReadableMapKeySetIterator iterator = toObj.keySetIterator();
        ArrayList<Address> toAddressList = new ArrayList();
        while (iterator.hasNextKey()) {
            String toMail = iterator.nextKey();
            String toName = toObj.getString(toMail);
            Address toAddress = new Address();
            toAddress.setDisplayName(toName);
            toAddress.setMailbox(toMail);
            toAddressList.add(toAddress);
        }

        messageHeader.setTo(toAddressList);

        ArrayList<Address> ccAddressList = new ArrayList();
        if (obj.hasKey("cc")) {
            ReadableMap ccObj = obj.getMap("cc");
            iterator = ccObj.keySetIterator();
            while (iterator.hasNextKey()) {
                String ccMail = iterator.nextKey();
                String ccName = ccObj.getString(ccMail);
                Address ccAddress = new Address();
                ccAddress.setDisplayName(ccName);
                ccAddress.setMailbox(ccMail);
                if(!ccAddress.mailbox().isEmpty()) ccAddressList.add(ccAddress);
            }
            messageHeader.setCc(ccAddressList);
        }

        ArrayList<Address> bccAddressList = new ArrayList();
        if (obj.hasKey("bcc")) {
            ReadableMap bccObj = obj.getMap("bcc");
            iterator = bccObj.keySetIterator();
            while (iterator.hasNextKey()) {
                String bccMail = iterator.nextKey();
                String bccName = bccObj.getString(bccMail);
                Address bccAddress = new Address();
                bccAddress.setDisplayName(bccName);
                bccAddress.setMailbox(bccMail);
                if(!bccAddress.mailbox().isEmpty()) bccAddressList.add(bccAddress);
            }
            messageHeader.setBcc(bccAddressList);
        }
        if (obj.hasKey("subject")) {
            messageHeader.setSubject(obj.getString("subject"));
        }
        final MessageBuilder messageBuilder = new MessageBuilder();
        messageBuilder.setHeader(messageHeader);
        if (obj.hasKey("body")) {
            messageBuilder.setHTMLBody(obj.getString("body"));
        }
        if (obj.hasKey("attachments")) {
            ReadableArray attachments = obj.getArray("attachments");
            for (int i = 0; i < attachments.size(); i++) {
                ReadableMap attachment = attachments.getMap(i);

                if (attachment.getString("uniqueId") != null)
                    continue;

                String pathName = attachment.getString("uri");
                String fileName = attachment.getString("filename");
                File file = new File(pathName);
                try {
                    long size = 0;
                    InputStream buf = null;
                    Uri uri = Uri.parse(pathName);
                    if (uri.getScheme().equals("content")) {
                        ContentResolver contentResolver = currentActivity.getContentResolver();
                        buf = contentResolver.openInputStream(uri);
                        size = contentResolver.openFileDescriptor(uri, "r").getStatSize();
                    } else {
                        buf = new BufferedInputStream(new FileInputStream(file));
                        size = file.length();
                    }
                    byte[] bytes = new byte[(int) size];


                    buf.read(bytes, 0, bytes.length);
                    buf.close();
                    WritableMap result = Arguments.createMap();
                    result.putString("status", "SUCCESS");
                    promise.resolve(result);
                    messageBuilder.addAttachment(Attachment.attachmentWithData(fileName, bytes));
                } catch (FileNotFoundException e) {
                    promise.reject("Attachments", e.getMessage());
                    return;
                } catch (IOException e) {
                    promise.reject("Attachments", e.getMessage());
                    return;
                }
            }
        }

        final ArrayList<Address> allRecipients = new ArrayList<>();
        allRecipients.addAll(toAddressList);
        if(!ccAddressList.isEmpty()) allRecipients.addAll(ccAddressList);
        if(!bccAddressList.isEmpty()) allRecipients.addAll(bccAddressList);

        if (obj.isNull("original_id")) {

            final SMTPOperation smtpOperation = smtpSession.sendMessageOperation(fromAddress, allRecipients, messageBuilder.data());
            currentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    smtpOperation.start(new OperationCallback() {
                        @Override
                        public void succeeded() {
                            WritableMap result = Arguments.createMap();
                            result.putString("status", "SUCCESS");
                            promise.resolve(result);
                        }

                        @Override
                        public void failed(MailException e) {
                            promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                        }
                    });
                }
            });
        } else {
            currentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    long original_id = (long) obj.getInt("original_id");
                    // Long original_id = ((Double)obj.getDouble("original_id")).longValue();
                    String original_folder = obj.getString("original_folder");
                    final IMAPFetchContentOperation fetchOriginalMessageOperation = imapSession.fetchMessageByUIDOperation(original_folder, original_id);
                    currentActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            fetchOriginalMessageOperation.start(new OperationCallback() {
                                @Override
                                public void succeeded() {
                                    MessageParser messageParser = MessageParser.messageParserWithData(fetchOriginalMessageOperation.data());

                                    // https://github.com/MailCore/mailcore2/blob/master/src/core/abstract/MCMessageHeader.cpp#L1197
                                    if (messageParser.header().messageID() != null) {
                                        messageBuilder.header().setInReplyTo(new ArrayList<>(Arrays.asList(messageParser.header().messageID())));
                                    }

                                    if (messageParser.header().references() != null) {
                                        ArrayList<String> newReferences = new ArrayList<>(messageParser.header().references());
                                        if (messageParser.header().messageID() != null) {
                                            newReferences.add(messageParser.header().messageID());
                                        }
                                        messageBuilder.header().setReferences(newReferences);
                                    }

                                    // set original attachments if they were any left
                                    if (obj.hasKey("attachments")) {
                                        ReadableArray attachments = obj.getArray("attachments");

                                        for (int i = 0; i < attachments.size(); i++) {
                                            ReadableMap attachment = attachments.getMap(i);

                                            if (attachment.getString("uniqueId") == null)
                                                continue;

                                            for (AbstractPart abstractPart : messageParser.attachments()) {
                                                if (abstractPart instanceof Attachment) {
                                                    Attachment original_attachment = (Attachment) abstractPart;

                                                    if (!original_attachment.uniqueID().equals(attachment.getString("uniqueId")))
                                                        continue;

                                                    messageBuilder.addAttachment(original_attachment);
                                                }
                                            }
                                        }
                                    }

                                    final SMTPOperation smtpOperation = smtpSession.sendMessageOperation(fromAddress, allRecipients, messageBuilder.data());
                                    smtpOperation.start(new OperationCallback() {
                                        @Override
                                        public void succeeded() {
                                            WritableMap result = Arguments.createMap();
                                            result.putString("status", "SUCCESS");
                                            promise.resolve(result);
                                        }

                                        @Override
                                        public void failed(MailException e) {
                                            promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                                        }
                                    });
                                }

                                @Override
                                public void failed(MailException e) {
                                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                                }
                            });
                        }
                    });
                }
            });
        }

    }
    public void saveDraft(final ReadableMap obj, final Promise promise, final Activity currentActivity) {
        String userEmail = obj.getString("email");
        MessageHeader messageHeader = new MessageHeader();
        if (obj.hasKey("headers")) {
            ReadableMap headerObj = obj.getMap("headers");
            ReadableMapKeySetIterator headerIterator = headerObj.keySetIterator();
            while (headerIterator.hasNextKey()) {
                String header = headerIterator.nextKey();
                String headerValue = headerObj.getString(header);
                messageHeader.setExtraHeader(header, headerValue);
            }
        }
        ReadableMap fromObj = obj.getMap("from");
        final Address fromAddress = new Address();
        fromAddress.setDisplayName(fromObj.getString("addressWithDisplayName"));
        fromAddress.setMailbox(fromObj.getString("mailbox"));
        messageHeader.setFrom(fromAddress);

        ReadableMap toObj = obj.getMap("to");
        ReadableMapKeySetIterator iterator = toObj.keySetIterator();
        ArrayList<Address> toAddressList = new ArrayList();
        while (iterator.hasNextKey()) {
            String toMail = iterator.nextKey();
            String toName = toObj.getString(toMail);
            Address toAddress = new Address();
            toAddress.setDisplayName(toName);
            toAddress.setMailbox(toMail);
            toAddressList.add(toAddress);
        }

        messageHeader.setTo(toAddressList);

        ArrayList<Address> ccAddressList = new ArrayList();
        if (obj.hasKey("cc")) {
            ReadableMap ccObj = obj.getMap("cc");
            iterator = ccObj.keySetIterator();
            while (iterator.hasNextKey()) {
                String ccMail = iterator.nextKey();
                String ccName = ccObj.getString(ccMail);
                Address ccAddress = new Address();
                ccAddress.setDisplayName(ccName);
                ccAddress.setMailbox(ccMail);
                if(!ccAddress.mailbox().isEmpty()) ccAddressList.add(ccAddress);
            }
            messageHeader.setCc(ccAddressList);
        }

        ArrayList<Address> bccAddressList = new ArrayList();
        if (obj.hasKey("bcc")) {
            ReadableMap bccObj = obj.getMap("bcc");
            iterator = bccObj.keySetIterator();
            while (iterator.hasNextKey()) {
                String bccMail = iterator.nextKey();
                String bccName = bccObj.getString(bccMail);
                Address bccAddress = new Address();
                bccAddress.setDisplayName(bccName);
                bccAddress.setMailbox(bccMail);
                if(!bccAddress.mailbox().isEmpty()) bccAddressList.add(bccAddress);
            }
            messageHeader.setBcc(bccAddressList);
        }
        if (obj.hasKey("subject")) {
            messageHeader.setSubject(obj.getString("subject"));
        }
        final MessageBuilder messageBuilder = new MessageBuilder();
        messageBuilder.setHeader(messageHeader);
        if (obj.hasKey("body")) {
            messageBuilder.setHTMLBody(obj.getString("body"));
        }
        if (obj.hasKey("attachments")) {
            ReadableArray attachments = obj.getArray("attachments");
            for (int i = 0; i < attachments.size(); i++) {
                ReadableMap attachment = attachments.getMap(i);

                if (attachment.getString("uniqueId") != null)
                    continue;

                String pathName = attachment.getString("uri");
                String fileName = attachment.getString("filename");
                File file = new File(pathName);
                try {
                    long size = 0;
                    InputStream buf = null;
                    Uri uri = Uri.parse(pathName);
                    if (uri.getScheme().equals("content")) {
                        ContentResolver contentResolver = currentActivity.getContentResolver();
                        buf = contentResolver.openInputStream(uri);
                        size = contentResolver.openFileDescriptor(uri, "r").getStatSize();
                    } else {
                        buf = new BufferedInputStream(new FileInputStream(file));
                        size = file.length();
                    }
                    byte[] bytes = new byte[(int) size];


                    buf.read(bytes, 0, bytes.length);
                    buf.close();
                    WritableMap result = Arguments.createMap();
                    result.putString("status", bytes.toString());
                    promise.resolve(result);
                    messageBuilder.addAttachment(Attachment.attachmentWithData(fileName, bytes));
                } catch (FileNotFoundException e) {
                    promise.reject("Attachments", e.getMessage());
                    return;
                } catch (IOException e) {
                    promise.reject("Attachments", e.getMessage());
                    return;
                }
            }
        }

        final ArrayList<Address> allRecipients = new ArrayList<>();
        allRecipients.addAll(toAddressList);
        if(!ccAddressList.isEmpty()) allRecipients.addAll(ccAddressList);
        if(!bccAddressList.isEmpty()) allRecipients.addAll(bccAddressList);
        String folder = obj.getString("folder");
        if (IMAPSessions.containsKey(userEmail)) {
            if (obj.isNull("original_id")) {
                final IMAPAppendMessageOperation appendMessageOperation = IMAPSessions.get(userEmail).appendMessageOperation(folder, messageBuilder.data(), 0);
                currentActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        appendMessageOperation.start(new OperationCallback() {
                            @Override
                            public void succeeded() {
                                WritableMap result = Arguments.createMap();
                                result.putString("status", "SUCCESS");
                                Log.d("LogFetch", "PEREMOGA!");
                                promise.resolve(result);
                            }

                            @Override
                            public void failed(MailException e) {
                                Log.d("LogFetch", "ZRDA");
                                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                            }
                        });
                    }
                });
            }
        }
    }
    public void getMail(final ReadableMap obj, final Promise promise) {
        final String folder = obj.getString("folder");
        final int messageId = obj.getInt("messageId");
        int requestKind = obj.getInt("requestKind");
        final String userEmail = obj.getString("email");
        if (IMAPSessions.containsKey(userEmail)) {
            final IMAPFetchMessagesOperation messagesOperation = IMAPSessions.get(userEmail).fetchMessagesByUIDOperation(folder, requestKind, IndexSet.indexSetWithIndex(messageId));

            if (obj.hasKey("headers")) {
                ReadableArray headersArray = obj.getArray("headers");
                List<String> extraHeaders = new ArrayList<>();
                for (int i = 0; headersArray.size() > i; i++) {
                    extraHeaders.add(headersArray.getString(i));
                }
                messagesOperation.setExtraHeaders(extraHeaders);
            }

            messagesOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    List<IMAPMessage> messages = messagesOperation.messages();
                    if (messages.isEmpty()) {
                        Log.d("messageId not found!", String.valueOf(messageId));
                        promise.reject("Mail not found!");
                        return;
                    }
                    final IMAPMessage message = messages.get(0);
                    final IMAPFetchParsedContentOperation imapFetchParsedContentOperation = IMAPSessions.get(userEmail).fetchParsedMessageByUIDOperation(folder, message.uid());
                    imapFetchParsedContentOperation.start(new OperationCallback() {
                        @Override
                        public void succeeded() {
                            WritableMap mailData = Arguments.createMap();
                            final Long uid = message.uid();
                            mailData.putInt("id", uid.intValue());
                            mailData.putString("date", message.header().date().toString());
                            WritableMap fromData = Arguments.createMap();
                            fromData.putString("mailbox", message.header().from().mailbox());
                            mailData.putInt("flags", message.flags());
                            fromData.putString("displayName", message.header().from().displayName());
                            mailData.putMap("from", fromData);
                            if (message.header().to() != null) {
                                WritableMap toData = Arguments.createMap();
                                ListIterator<Address> toIterator = message.header().to().listIterator();
                                while (toIterator.hasNext()) {
                                    Address toAddress = toIterator.next();
                                    toData.putString(toAddress.mailbox(), toAddress.displayName());
                                }
                                mailData.putMap("to", toData);
                            }
                            if (message.header().cc() != null) {
                                WritableMap ccData = Arguments.createMap();
                                ListIterator<Address> ccIterator = message.header().cc().listIterator();
                                while (ccIterator.hasNext()) {
                                    Address ccAddress = ccIterator.next();
                                    ccData.putString(ccAddress.mailbox(), ccAddress.displayName());
                                }
                                mailData.putMap("cc", ccData);
                            }
                            if (message.header().bcc() != null) {
                                WritableMap bccData = Arguments.createMap();
                                ListIterator<Address> bccIterator = message.header().bcc().listIterator();
                                while (bccIterator.hasNext()) {
                                    Address bccAddress = bccIterator.next();
                                    bccData.putString(bccAddress.mailbox(), bccAddress.displayName());
                                }
                                mailData.putMap("bcc", bccData);
                            }
                            mailData.putString("subject", message.header().subject());
                            MessageParser parser = imapFetchParsedContentOperation.parser();
                            mailData.putString("body", parser.htmlBodyRendering());
                            mailData.putString("plainBody", parser.plainTextBodyRendering(true));

                            WritableMap attachmentsData = Arguments.createMap();
                            List<AbstractPart> attachments = message.attachments();
                            if (!attachments.isEmpty()) {
                                for (AbstractPart attachment : message.attachments()) {
                                    IMAPPart part = (IMAPPart) attachment;
                                    WritableMap attachmentData = Arguments.createMap();
                                    attachmentData.putString("filename", attachment.filename());

                                    Long size = part.size();
                                    attachmentData.putString("size", size.toString());

                                    attachmentData.putInt("encoding", part.encoding());
                                    attachmentData.putString("uniqueId", part.uniqueID());

                                    attachmentsData.putMap(part.partID(), attachmentData);
                                }
                            }
                            mailData.putMap("attachments", attachmentsData);

                            if (!message.htmlInlineAttachments().isEmpty()) {
                                WritableMap attachmentsDataInline = Arguments.createMap();
                                List<AbstractPart> attachmentsInline = message.htmlInlineAttachments();

                                for (AbstractPart attachment : attachmentsInline) {
                                    IMAPPart part = (IMAPPart) attachment;
                                    WritableMap attachmentData = Arguments.createMap();
                                    attachmentData.putString("filename", attachment.filename());

                                    Long size = part.size();
                                    attachmentData.putString("size", size.toString());
                                    attachmentData.putString("cid", attachment.contentID());
                                    attachmentData.putString("partID", ((IMAPPart) attachment).partID());
                                    attachmentData.putInt("encoding", part.encoding());
                                    attachmentData.putString("uniqueId", part.uniqueID());
                                    attachmentData.putString("mimepart", attachment.mimeType());

                                    attachmentsDataInline.putMap(part.partID(), attachmentData);
                                }
                                mailData.putMap("inline", attachmentsDataInline);
                            }

                            // Process fetched headers from mail
                            WritableMap headerData = Arguments.createMap();
                            headerData.putString("gmailMessageID", Long.toString(message.gmailMessageID()));
                            headerData.putString("gmailThreadID", Long.toString(message.gmailThreadID()));

                            ListIterator<String> headerIterator = message.header().allExtraHeadersNames().listIterator();
                            while (headerIterator.hasNext()) {
                                String headerKey = headerIterator.next();
                                headerData.putString(headerKey, message.header().extraHeaderValueForName(headerKey));
                            }
                            mailData.putMap("headers", headerData);

                            mailData.putString("status", "success");
                            promise.resolve(mailData);
                        }

                        @Override
                        public void failed(MailException e) {
                            promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                        }
                    });
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        }

    }

    public void createFolderLabel(final ReadableMap obj, final Promise promise) {
        IMAPOperation imapOperation = this.imapSession.createFolderOperation(obj.getString("folder"));
        imapOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                WritableMap result = Arguments.createMap();
                result.putString("status", "SUCCESS");
                promise.resolve(result);
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }

    public void renameFolderLabel(final ReadableMap obj, final Promise promise) {
        IMAPOperation imapOperation = this.imapSession.renameFolderOperation(obj.getString("folderOldName"), obj.getString("folderNewName"));
        imapOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                WritableMap result = Arguments.createMap();
                result.putString("status", "SUCCESS");
                promise.resolve(result);
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }

    public void deleteFolderLabel(final ReadableMap obj, final Promise promise) {
        IMAPOperation imapOperation = this.imapSession.deleteFolderOperation(obj.getString("folder"));
        imapOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                WritableMap result = Arguments.createMap();
                result.putString("status", "SUCCESS");
                promise.resolve(result);
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }

    public void getFolders(final ReadableMap obj, final Promise promise) {
        String userEmail = obj.getString("email");
        if (IMAPSessions.containsKey(userEmail)) {
            final IMAPFetchFoldersOperation foldersOperation = IMAPSessions.get(userEmail).fetchAllFoldersOperation();
            foldersOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    List<IMAPFolder> folders = foldersOperation.folders();
                    WritableMap result = Arguments.createMap();
                    WritableArray a = new WritableNativeArray();
                    result.putString("status", "SUCCESS");
                    for (IMAPFolder folder : folders) {
                        WritableMap mapFolder = Arguments.createMap();
                        mapFolder.putString("path", folder.path());
                        mapFolder.putInt("flags", folder.flags());
                        a.pushMap(mapFolder);
                    }
                    result.putArray("folders", a);
                    promise.resolve(result);
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        }
    }

    public void moveEmail(final ReadableMap obj, final Promise promise) {
        String from = obj.getString("folderFrom");
        int messageId = obj.getInt("messageId");
        String to = obj.getString("folderTo");
        String userEmail = obj.getString("email");
        if (IMAPSessions.containsKey(userEmail)) {
            IMAPOperation imapOperation = IMAPSessions.get(userEmail).copyMessagesOperation(from, IndexSet.indexSetWithIndex(messageId), to);
            imapOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });

            WritableMap permantDeleteRequest = Arguments.createMap();
            permantDeleteRequest.putString("folder", from);
            permantDeleteRequest.putString("email", userEmail);
            permantDeleteRequest.putInt("messageId", messageId);
            permantDelete(permantDeleteRequest, promise);
        }
    }

    public void permantDelete(final ReadableMap obj, final Promise promise) {
        String folder = obj.getString("folder");
        int messageId = obj.getInt("messageId");
        String userEmail = obj.getString("email");
        if (IMAPSessions.containsKey(userEmail)) {
            IMAPOperation imapOperation = IMAPSessions.get(userEmail).storeFlagsByUIDOperation(folder, IndexSet.indexSetWithIndex(messageId), 0, MessageFlag.MessageFlagDeleted);
            imapOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
            imapOperation = IMAPSessions.get(userEmail).expungeOperation(folder);
            imapOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    WritableMap result = Arguments.createMap();
                    result.putString("status", "SUCCESS");
                    promise.resolve(result);
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        }
    }

    public void ActionLabelMessage(final ReadableMap obj, final Promise promise) {
        String folder = obj.getString("folder");
        int messageId = obj.getInt("messageId");
        int flag = obj.getInt("flagsRequestKind");
        ReadableArray listTags = obj.getArray("tags");
        String userEmail = obj.getString("email");

        List<String> tags = new ArrayList<String>();
        for (int i = 0; i < listTags.size(); i++) {
            tags.add(listTags.getString(i));
        }
        if (IMAPSessions.containsKey(userEmail)) {
            IMAPOperation imapOperation = IMAPSessions.get(userEmail).storeLabelsByUIDOperation(folder, IndexSet.indexSetWithIndex(messageId), flag, tags);
            imapOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    WritableMap result = Arguments.createMap();
                    result.putString("status", "SUCCESS");
                    promise.resolve(result);
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        }
    }

    public void ActionFlagMessage(final ReadableMap obj, final Promise promise) {
        String folder = obj.getString("folder");
        int messageId = obj.getInt("messageId");
        int flag = obj.getInt("flagsRequestKind");
        int messageFlag = obj.getInt("messageFlag");
        String userEmail = obj.getString("email");
        if (IMAPSessions.containsKey(userEmail)) {
            IMAPOperation imapOperation = IMAPSessions.get(userEmail).storeFlagsByUIDOperation(folder, IndexSet.indexSetWithIndex(messageId), flag, messageFlag);
            imapOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    WritableMap result = Arguments.createMap();
                    result.putString("status", "SUCCESS");
                    promise.resolve(result);
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        }
    }

    public void getMails(final ReadableMap obj, final Promise promise) {
        final String folder = obj.getString("folder");
        final int requestKind = obj.getInt("requestKind");
        final Long threadId = obj.hasKey("threadId") ? Long.parseLong(obj.getString("threadId")) : null;
        if (threadId == null) {
            IndexSet indexSet = IndexSet.indexSetWithRange(new Range(1, Long.MAX_VALUE));
            final IMAPFetchMessagesOperation messagesOperation = imapSession.fetchMessagesByUIDOperation(folder, requestKind, indexSet);

            if (obj.hasKey("headers")) {
                ReadableArray headersArray = obj.getArray("headers");
                List<String> extraHeaders = new ArrayList<>();
                for (int i = 0; headersArray.size() > i; i++) {
                    extraHeaders.add(headersArray.getString(i));
                }
                messagesOperation.setExtraHeaders(extraHeaders);
            }

            final WritableMap result = Arguments.createMap();
            final WritableArray mails = Arguments.createArray();
            messagesOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    List<IMAPMessage> messages = messagesOperation.messages();
                    if (messages.isEmpty()) {
                        promise.reject("Mails not found!");
                        return;
                    }
                    ArrayList<Long> ids = new ArrayList();
                    for (final IMAPMessage message : messages) {
                        final WritableMap mailData = Arguments.createMap();
                        WritableMap headerData = Arguments.createMap();
                        ListIterator<String> headerIterator = message.header().allExtraHeadersNames().listIterator();
                        while (headerIterator.hasNext()) {
                            String headerKey = headerIterator.next();
                            headerData.putString(headerKey, message.header().extraHeaderValueForName(headerKey));
                        }
                        mailData.putMap("headers", headerData);
                        Long mailId = message.uid();
                        ids.add(mailId);
                        mailData.putInt("id", mailId.intValue());
                        mailData.putInt("flags", message.flags());
                        mailData.putString("from", message.header().from().displayName());
                        mailData.putString("fromMailbox", message.header().from().mailbox());
                        mailData.putString("subject", message.header().subject());
                        mailData.putString("date", message.header().date().toString());
                        mailData.putInt("attachments", message.attachments().size());
                        message.requiredPartsForRendering();
                        mails.pushMap(mailData);
                    }

                    result.putString("status", "SUCCESS");
                    result.putArray("mails", mails);
                    promise.resolve(result);
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        } else {

            final IMAPSearchOperation imapOperation = imapSession.searchOperation(folder, IMAPSearchExpression.searchGmailThreadID(threadId));
            imapOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    final IMAPFetchMessagesOperation messagesOperation = imapSession.fetchMessagesByUIDOperation(folder, requestKind, imapOperation.uids());

                    if (obj.hasKey("headers")) {
                        ReadableArray headersArray = obj.getArray("headers");
                        List<String> extraHeaders = new ArrayList<>();
                        for (int i = 0; headersArray.size() > i; i++) {
                            extraHeaders.add(headersArray.getString(i));
                        }
                        messagesOperation.setExtraHeaders(extraHeaders);
                    }

                    final WritableMap result = Arguments.createMap();
                    final WritableArray mails = Arguments.createArray();
                    messagesOperation.start(new OperationCallback() {
                        @Override
                        public void succeeded() {
                            List<IMAPMessage> messages = messagesOperation.messages();
                            if (messages.isEmpty()) {
                                promise.reject("Mails not found!");
                                return;
                            }
                            for (final IMAPMessage message : messages) {
                                final WritableMap mailData = Arguments.createMap();
                                WritableMap headerData = Arguments.createMap();
                                ListIterator<String> headerIterator = message.header().allExtraHeadersNames().listIterator();
                                while (headerIterator.hasNext()) {
                                    String headerKey = headerIterator.next();
                                    headerData.putString(headerKey, message.header().extraHeaderValueForName(headerKey));
                                }
                                mailData.putMap("headers", headerData);
                                Long mailId = message.uid();
                                mailData.putInt("id", mailId.intValue());
                                mailData.putInt("flags", message.flags());
                                mailData.putString("from", message.header().from().displayName());
                                mailData.putString("subject", message.header().subject());
                                mailData.putString("date", message.header().date().toString());
                                mailData.putInt("attachments", message.attachments().size());

                                mails.pushMap(mailData);
                            }
                            result.putString("status", "SUCCESS");
                            result.putArray("mails", mails);
                            promise.resolve(result);
                        }

                        @Override
                        public void failed(MailException e) {
                            promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                        }
                    });

                }

                @Override
                public void failed(MailException e) {

                }
            });
        }
    }

    public void getMailsWithContent(final ReadableMap obj, final Promise promise) {
        final String folder = obj.getString("folder");
        final int requestKind = obj.getInt("requestKind");
        final Long threadId = obj.hasKey("threadId") ? Long.parseLong(obj.getString("threadId")) : null;

        if (threadId == null) {
            IndexSet indexSet = IndexSet.indexSetWithRange(new Range(1, Long.MAX_VALUE));
            final IMAPFetchMessagesOperation messagesOperation = imapSession.fetchMessagesByUIDOperation(folder, requestKind, indexSet);

            if (obj.hasKey("headers")) {
                ReadableArray headersArray = obj.getArray("headers");
                List<String> extraHeaders = new ArrayList<>();
                for (int i = 0; headersArray.size() > i; i++) {
                    extraHeaders.add(headersArray.getString(i));
                }
                messagesOperation.setExtraHeaders(extraHeaders);
            }

            final WritableMap result = Arguments.createMap();
            final WritableArray mails = Arguments.createArray();
            final WritableArray mailsContent = Arguments.createArray();
            messagesOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    List<IMAPMessage> messages = messagesOperation.messages();
                    if (messages.isEmpty()) {
                        promise.reject("Mails not found!");
                        return;
                    }
                    ArrayList<Long> ids = new ArrayList();
                    for (final IMAPMessage message : messages) {
                        final WritableMap mailData = Arguments.createMap();
                        WritableMap headerData = Arguments.createMap();
                        ListIterator<String> headerIterator = message.header().allExtraHeadersNames().listIterator();
                        while (headerIterator.hasNext()) {
                            String headerKey = headerIterator.next();
                            headerData.putString(headerKey, message.header().extraHeaderValueForName(headerKey));
                        }
                        mailData.putMap("headers", headerData);
                        Long mailId = message.uid();
                        ids.add(mailId);
                        mailData.putInt("id", mailId.intValue());
                        mailData.putInt("flags", message.flags());
                        mailData.putString("from", message.header().from().displayName());
                        mailData.putString("fromMailbox", message.header().from().mailbox());
                        mailData.putString("subject", message.header().subject());
                        mailData.putString("date", message.header().date().toString());
                        mailData.putInt("attachments", message.attachments().size());
                        message.requiredPartsForRendering();
                        mails.pushMap(mailData);
                    }
                    getMailContent(ids, folder, new MessageBodyCallback() {
                        @Override
                        public void receivedMessage(ArrayList<Pair<Long, String>> emailData) {
                            result.putString("status", "SUCCESS");
                            result.putArray("mails", mails);
                            for (Pair<Long, String> emailDatum : emailData) {
                                final WritableMap content = Arguments.createMap();
                                content.putInt("id", emailDatum.first.intValue());
                                content.putString("content", emailDatum.second);
                                mailsContent.pushMap(content);
                            }
                            result.putArray("mail_content", mailsContent);
                            promise.resolve(result);
                        }
                    });
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        } else {

            final IMAPSearchOperation imapOperation = imapSession.searchOperation(folder, IMAPSearchExpression.searchGmailThreadID(threadId));
            imapOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    final IMAPFetchMessagesOperation messagesOperation = imapSession.fetchMessagesByUIDOperation(folder, requestKind, imapOperation.uids());

                    if (obj.hasKey("headers")) {
                        ReadableArray headersArray = obj.getArray("headers");
                        List<String> extraHeaders = new ArrayList<>();
                        for (int i = 0; headersArray.size() > i; i++) {
                            extraHeaders.add(headersArray.getString(i));
                        }
                        messagesOperation.setExtraHeaders(extraHeaders);
                    }

                    final WritableMap result = Arguments.createMap();
                    final WritableArray mails = Arguments.createArray();
                    messagesOperation.start(new OperationCallback() {
                        @Override
                        public void succeeded() {
                            List<IMAPMessage> messages = messagesOperation.messages();
                            if (messages.isEmpty()) {
                                promise.reject("Mails not found!");
                                return;
                            }
                            for (final IMAPMessage message : messages) {
                                final WritableMap mailData = Arguments.createMap();
                                WritableMap headerData = Arguments.createMap();
                                ListIterator<String> headerIterator = message.header().allExtraHeadersNames().listIterator();
                                while (headerIterator.hasNext()) {
                                    String headerKey = headerIterator.next();
                                    headerData.putString(headerKey, message.header().extraHeaderValueForName(headerKey));
                                }
                                mailData.putMap("headers", headerData);
                                Long mailId = message.uid();
                                mailData.putInt("id", mailId.intValue());
                                mailData.putInt("flags", message.flags());
                                mailData.putString("from", message.header().from().displayName());
                                mailData.putString("subject", message.header().subject());
                                mailData.putString("date", message.header().date().toString());
                                mailData.putInt("attachments", message.attachments().size());

                                mails.pushMap(mailData);
                            }
                            result.putString("status", "SUCCESS");
                            result.putArray("mails", mails);
                            promise.resolve(result);
                        }

                        @Override
                        public void failed(MailException e) {
                            promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                        }
                    });

                }

                @Override
                public void failed(MailException e) {

                }
            });
        }
    }

    interface MessageBodyCallback {
        void receivedMessage(ArrayList<Pair<Long, String>> emailData);
    }

    private void getMailContent(final ArrayList<Long> mailId, final String folder, final MessageBodyCallback callback) {
        final ArrayList<Pair<Long, String>> emailData = new ArrayList();
        if (!mailId.isEmpty()) {
            for (final Long id : mailId) {
                final IMAPFetchContentOperation operation = imapSession.fetchMessageByUIDOperation(folder, id.intValue(), true);
                operation.start(new OperationCallback() {
                    @Override
                    public void succeeded() {
                        MessageParser parser = MessageParser.messageParserWithData(operation.data());
                        emailData.add(Pair.create(id, parser.plainTextRendering())); // PLain text here
                        if (mailId.size() == emailData.size()) {
                            callback.receivedMessage(emailData);
                        }
                    }

                    @Override
                    public void failed(MailException e) {
                        Log.d("LogFetch", "Zrada");
                    }
                });
            }
        } else {
            callback.receivedMessage(emailData);
        }

    }


    public void getMailsThread(final ReadableMap obj, final Promise promise) {
        final String folder = obj.getString("folder");
        int requestKind = obj.getInt("requestKind");
        int lastUId = obj.hasKey("lastUId") ? obj.getInt("lastUId") : 1;
        IndexSet indexSet = IndexSet.indexSetWithRange(new Range(lastUId, Long.MAX_VALUE));
        final IMAPFetchMessagesOperation messagesOperation = imapSession.fetchMessagesByUIDOperation(folder, requestKind, indexSet);

        if (obj.hasKey("headers")) {
            ReadableArray headersArray = obj.getArray("headers");
            List<String> extraHeaders = new ArrayList<>();
            for (int i = 0; headersArray.size() > i; i++) {
                extraHeaders.add(headersArray.getString(i));
            }
            messagesOperation.setExtraHeaders(extraHeaders);
        }

        final WritableMap result = Arguments.createMap();
        final WritableArray mails = Arguments.createArray();
        final List<String> listThreads = new ArrayList<>();
        messagesOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                List<IMAPMessage> messages = messagesOperation.messages();
                Collections.reverse(messages);
                if (messages.isEmpty()) {
                    promise.reject("Mails not found!");
                    return;
                }
                for (final IMAPMessage message : messages) {
                    if (!listThreads.contains(message.header().messageID())) {
                        final WritableMap mailData = Arguments.createMap();
                        WritableMap headerData = Arguments.createMap();
                        listThreads.add(message.header().messageID());
                        if (message.header().references() != null) {
                            listThreads.addAll(message.header().references());
                            mailData.putInt("thread", message.header().references().size() + 1);
                        }
                        ListIterator<String> headerIterator = message.header().allExtraHeadersNames().listIterator();
                        headerData.putString("gmailMessageID", Long.toString(message.gmailMessageID()));
                        headerData.putString("gmailThreadID", Long.toString(message.gmailThreadID()));
                        while (headerIterator.hasNext()) {
                            String headerKey = headerIterator.next();
                            headerData.putString(headerKey, message.header().extraHeaderValueForName(headerKey));
                        }
                        mailData.putMap("headers", headerData);
                        Long mailId = message.uid();
                        mailData.putInt("id", mailId.intValue());
                        mailData.putInt("flags", message.flags());
                        mailData.putString("from", message.header().from().displayName());
                        mailData.putString("subject", message.header().subject());
                        mailData.putString("date", message.header().date().toString());
                        mailData.putInt("attachments", message.attachments().size());
                        mails.pushMap(mailData);
                    }
                }

                result.putString("status", "SUCCESS");
                result.putArray("mails", mails);
                promise.resolve(result);
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }

    public void getAttachment(final ReadableMap obj, final Promise promise) {
        final String filename = obj.getString("filename");
        String folderId = obj.getString("folder");
        long messageId = (long) obj.getInt("messageId");
        String partID = obj.getString("partID");
        int encoding = obj.getInt("encoding");
        final String folderOutput = obj.getString("folderOutput");
        final IMAPFetchContentOperation imapOperation = imapSession.fetchMessageAttachmentByUIDOperation(folderId, messageId, partID, encoding, true);
        imapOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                File file = new File(folderOutput, filename);
                try {
                    FileOutputStream outputStream;
                    outputStream = new FileOutputStream(file);
                    outputStream.write(imapOperation.data());
                    outputStream.close();
                    if (file.canWrite()) {
                        WritableMap result = Arguments.createMap();
                        result.putString("status", "SUCCESS");
                        promise.resolve(result);
                    }
                } catch (FileNotFoundException e) {
                    promise.reject(e.getMessage());
                } catch (IOException e) {
                    promise.reject(e.getMessage());
                } catch (Exception e) {
                    promise.reject(e.getMessage());
                }
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }

    public void getAttachmentInline(final ReadableMap obj, final Promise promise) {
        final String filename = obj.getString("filename");
        String folderId = obj.getString("folder");
        long messageId = (long) obj.getInt("messageId");
        String partID = obj.getString("partID");
        int encoding = obj.getInt("encoding");
        final String mimepart = obj.getString("mimepart");

        final IMAPFetchContentOperation imapOperation = imapSession.fetchMessageAttachmentByUIDOperation(folderId, messageId, partID, encoding, true);
        imapOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                WritableMap result = Arguments.createMap();
                String data = "data:" + mimepart + ";base64, " + Base64.encodeToString(imapOperation.data(), Base64.DEFAULT);
                result.putString("data", data);
                promise.resolve(result);
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }

    public void statusFolder(final ReadableMap obj, final Promise promise) {
        String folder = obj.getString("folder");
        String userEmail = obj.getString("email");
        if (IMAPSessions.containsKey(userEmail)) {
            final IMAPFolderStatusOperation folderStatusOperation = IMAPSessions.get(userEmail).folderStatusOperation(folder);
            folderStatusOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    WritableMap result = Arguments.createMap();
                    result.putString("status", "SUCCESS");
                    result.putInt("unseenCount", (int) folderStatusOperation.status().unseenCount());
                    result.putInt("messageCount", (int) folderStatusOperation.status().messageCount());
                    result.putInt("recentCount", (int) folderStatusOperation.status().recentCount());
                    promise.resolve(result);
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        }
    }
    public void safeStatusFolder(final ReadableMap obj, final Promise promise) {
        String folder = obj.getString("folder");

        final IMAPFolderStatusOperation folderStatusOperation = safeImapSession.folderStatusOperation(folder);
        folderStatusOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                WritableMap result = Arguments.createMap();
                result.putString("status", "SUCCESS");
                result.putInt("unseenCount", (int) folderStatusOperation.status().unseenCount());
                result.putInt("messageCount", (int) folderStatusOperation.status().messageCount());
                result.putInt("recentCount", (int) folderStatusOperation.status().recentCount());
                promise.resolve(result);
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });

    }

    public void getMailsByRange(final ReadableMap obj, final Promise promise) {
        String userEmail = obj.getString("email");
        final String folder = obj.getString("folder");
        final int requestKind = obj.getInt("requestKind");
        final int from = obj.getInt("from");
        final int length = obj.getInt("length");

        // Build operation
        IndexSet indexSet = IndexSet.indexSetWithRange(new Range(from, length));

        if (IMAPSessions.containsKey(userEmail)) {
            final IMAPFetchMessagesOperation messagesOperation = IMAPSessions.get(userEmail).fetchMessagesByNumberOperation(folder, requestKind, indexSet);
            if (obj.hasKey("headers")) {
                ReadableArray headersArray = obj.getArray("headers");
                List<String> extraHeaders = new ArrayList<>();
                for (int i = 0; headersArray.size() > i; i++) {
                    extraHeaders.add(headersArray.getString(i));
                }
                messagesOperation.setExtraHeaders(extraHeaders);
            }

            // Start operation
            messagesOperation.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    parseMessages(messagesOperation.messages(), promise);
                }

                @Override
                public void failed(MailException e) {
                    promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                }
            });
        }
    }
    public void safeGetMailsByRange(final ReadableMap obj, final Promise promise) {
        // Get arguments
        final String folder = obj.getString("folder");
        final int requestKind = obj.getInt("requestKind");
        final int from = obj.getInt("from");
        final int length = obj.getInt("length");

        // Build operation
        IndexSet indexSet = IndexSet.indexSetWithRange(new Range(from, length));
        final IMAPFetchMessagesOperation messagesOperation = safeImapSession.fetchMessagesByNumberOperation(folder, requestKind, indexSet);

        if (obj.hasKey("headers")) {
            ReadableArray headersArray = obj.getArray("headers");
            List<String> extraHeaders = new ArrayList<>();
            for (int i = 0; headersArray.size() > i; i++) {
                extraHeaders.add(headersArray.getString(i));
            }
            messagesOperation.setExtraHeaders(extraHeaders);
        }

        // Start operation
        messagesOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                parseMessages(messagesOperation.messages(), promise);
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }
    public void getMailsByRangeWithContent(final ReadableMap obj, final Promise promise) {
        // Get arguments
        final String folder = obj.getString("folder");
        final int requestKind = obj.getInt("requestKind");
        final int from = obj.getInt("from");
        final int length = obj.getInt("length");

        // Build operation
        IndexSet indexSet = IndexSet.indexSetWithRange(new Range(from, length));
        final IMAPFetchMessagesOperation messagesOperation = imapSession.fetchMessagesByNumberOperation(folder, requestKind, indexSet);

        if (obj.hasKey("headers")) {
            ReadableArray headersArray = obj.getArray("headers");
            List<String> extraHeaders = new ArrayList<>();
            for (int i = 0; headersArray.size() > i; i++) {
                extraHeaders.add(headersArray.getString(i));
            }
            messagesOperation.setExtraHeaders(extraHeaders);
        }

        // Start operation
        messagesOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                parseMessagesWithContent(messagesOperation.messages(), promise, folder);
            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }

    public void getMailsByThread(final ReadableMap obj, final Promise promise) {
        // Get arguments
        final String folder = obj.getString("folder");
        final int requestKind = obj.getInt("requestKind");
        final Long threadId = Long.parseLong(obj.getString("threadId"));

        // Build operation
        final IMAPSearchOperation searchOperation = imapSession.searchOperation(
                folder, IMAPSearchExpression.searchGmailThreadID(threadId));

        // Start searchOperation
        searchOperation.start(new OperationCallback() {
            @Override
            public void succeeded() {
                // Build operation
                final IMAPFetchMessagesOperation messagesOperation = imapSession
                        .fetchMessagesByUIDOperation(folder, requestKind, searchOperation.uids());

                if (obj.hasKey("headers")) {
                    ReadableArray headersArray = obj.getArray("headers");
                    List<String> extraHeaders = new ArrayList<>();
                    for (int i = 0; headersArray.size() > i; i++) {
                        extraHeaders.add(headersArray.getString(i));
                    }
                    messagesOperation.setExtraHeaders(extraHeaders);
                }

                // Start messagesOperation
                messagesOperation.start(new OperationCallback() {
                    @Override
                    public void succeeded() {
                        parseMessages(messagesOperation.messages(), promise);
                    }

                    @Override
                    public void failed(MailException e) {
                        promise.reject(String.valueOf(e.errorCode()), e.getMessage());
                    }
                });

            }

            @Override
            public void failed(MailException e) {
                promise.reject(String.valueOf(e.errorCode()), e.getMessage());
            }
        });
    }

    private void parseMessages(List<IMAPMessage> messages, final Promise promise) {
        final WritableMap result = Arguments.createMap();
        final WritableArray mails = Arguments.createArray();

        if (messages == null) {
            result.putString("status", "SUCCESS");
            result.putArray("mails", mails);
            promise.resolve(result);
        }

        for (final IMAPMessage message : messages) {

            // Process fetched headers from mail
            WritableMap headerData = Arguments.createMap();
            headerData.putString("gmailMessageID", Long.toString(message.gmailMessageID()));
            headerData.putString("gmailThreadID", Long.toString(message.gmailThreadID()));
            ListIterator<String> headerIterator = message.header().allExtraHeadersNames().listIterator();
            while (headerIterator.hasNext()) {
                String headerKey = headerIterator.next();
                headerData.putString(headerKey, message.header().extraHeaderValueForName(headerKey));
            }

            // Process fetched data from mail
            final WritableMap mailData = Arguments.createMap();
            mailData.putMap("headers", headerData);
            mailData.putInt("id", ((Long) message.uid()).intValue());
            mailData.putInt("flags", message.flags());
            mailData.putString("from", message.header().from().displayName());
            mailData.putString("fromMailbox", message.header().from().mailbox());
            mailData.putString("subject", message.header().subject());
            mailData.putString("date", message.header().date().toString());
            mailData.putInt("attachments", message.attachments().size());

            mails.pushMap(mailData);
        }

        result.putString("status", "SUCCESS");
        result.putArray("mails", mails);
        promise.resolve(result);
    }

    private void parseMessagesWithContent(List<IMAPMessage> messages, final Promise promise, String folder) {
        final WritableMap result = Arguments.createMap();
        final WritableArray mails = Arguments.createArray();
        final WritableArray mailsContent = Arguments.createArray();

        if (messages == null) {
            result.putString("status", "SUCCESS");
            result.putArray("mails", mails);
            promise.resolve(result);
        }
        ArrayList<Long> ids = new ArrayList();

        for (final IMAPMessage message : messages) {

            // Process fetched headers from mail
            WritableMap headerData = Arguments.createMap();
            headerData.putString("gmailMessageID", Long.toString(message.gmailMessageID()));
            headerData.putString("gmailThreadID", Long.toString(message.gmailThreadID()));
            ListIterator<String> headerIterator = message.header().allExtraHeadersNames().listIterator();
            while (headerIterator.hasNext()) {
                String headerKey = headerIterator.next();
                headerData.putString(headerKey, message.header().extraHeaderValueForName(headerKey));
            }

            // Process fetched data from mail
            final WritableMap mailData = Arguments.createMap();
            mailData.putMap("headers", headerData);
            ids.add(message.uid());
            mailData.putInt("id", ((Long) message.uid()).intValue());
            mailData.putInt("flags", message.flags());
            mailData.putString("from", message.header().from().displayName());
            mailData.putString("fromMailbox", message.header().from().mailbox());
            mailData.putString("subject", message.header().subject());
            mailData.putString("date", message.header().date().toString());
            mailData.putInt("attachments", message.attachments().size());

            mails.pushMap(mailData);
        }

        getMailContent(ids, folder, new MessageBodyCallback() {
            @Override
            public void receivedMessage(ArrayList<Pair<Long, String>> emailData) {
                result.putString("status", "SUCCESS");
                result.putArray("mails", mails);
                for (Pair<Long, String> emailDatum : emailData) {
                    final WritableMap content = Arguments.createMap();
                    content.putInt("id", emailDatum.first.intValue());
                    content.putString("content", emailDatum.second);
                    mailsContent.pushMap(content);
                }
                result.putArray("mail_content", mailsContent);
                promise.resolve(result);
            }
        });
    }

}
