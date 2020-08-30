//
//  EmailHelper.h
//  RNMailCore
//
//  Created by devStar on 8/29/20.
//

#import <Foundation/Foundation.h>
#import <AppAuth/AppAuth.h>
#import <GTMAppAuth/GTMAppAuth.h>

@interface EmailHelper : NSObject

+ (EmailHelper *_Nonnull)singleton;

@property(nonatomic, strong, nullable) id<OIDExternalUserAgentSession> currentAuthorizationFlow;
@property(nonatomic, nullable) GTMAppAuthFetcherAuthorization *authorization;

- (void)doEmailLoginIfRequiredOnVC:(UIViewController*_Nonnull)vc completionBlock:(dispatch_block_t _Nonnull )completionBlock;

- (void)initialize:(NSString *_Nullable)clientID redirectURL:(NSString *_Nullable)redirectURL;

- (void)refreshState:(NSString *_Nullable)authorizerKey;

@end
