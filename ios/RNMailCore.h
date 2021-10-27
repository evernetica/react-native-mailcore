#import <MailCore/MailCore.h>
#if __has_include("RCTBridgeModule.h")
#import "RCTBridgeModule.h"
#else
#import <React/RCTBridgeModule.h>
#endif

@interface RNMailCore : NSObject <RCTBridgeModule>
    @property (strong, nonatomic) MCOSMTPSession *smtpObject;
    @property (strong, nonatomic) MCOIMAPSession *imapSession;
    @property (strong, nonatomic) MCOIMAPSession *safeImapSession;

    - (instancetype)init:(MCOSMTPSession *)smtpObject;
    - (instancetype)init:(MCOSMTPSession *)imapObject;
    - (instancetype)init:(MCOSMTPSession *)safeImapObject;

    - (MCOSMTPSession *) getSmtpObject;
    - (MCOSMTPSession *) getImapObject;
    - (MCOSMTPSession *) getSafeImapObject;
@end
  
