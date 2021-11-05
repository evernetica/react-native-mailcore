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
    @property (strong, nonatomic) NSMutableDictionary *IMAPSessions;
    
    - (instancetype)init;
//    - (instancetype)initSmtp:(MCOSMTPSession *)smtpObject;
//    - (instancetype)initImap:(MCOSMTPSession *)imapObject;
//    - (instancetype)init:(MCOSMTPSession *)safeImapObject;
//    - (instancetype)initIMAPSessions:(NSMutableDictionary *)IMAPSessions;

    - (MCOSMTPSession *) getSmtpObject;
    - (MCOSMTPSession *) getImapObject;
    
@end
  
