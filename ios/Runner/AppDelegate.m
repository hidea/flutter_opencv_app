#import "AppDelegate.h"
#import "GeneratedPluginRegistrant.h"
#include "OpenCV.h"

@implementation AppDelegate

static NSString *const OPENCV_CHANNEL = @"api.opencv.dev/opencv";

- (BOOL)application:(UIApplication *)application
    didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
  [GeneratedPluginRegistrant registerWithRegistry:self];

  FlutterViewController *controller = (FlutterViewController*)self.window.rootViewController;
  FlutterMethodChannel *opencvChannel = [FlutterMethodChannel
                                         methodChannelWithName:OPENCV_CHANNEL
                                         binaryMessenger:controller];

  [opencvChannel setMethodCallHandler:^(FlutterMethodCall* call, FlutterResult result) {
    if ([@"toPerspectiveTransformation" isEqualToString:call.method]) {
      NSString *srcpath = call.arguments[@"srcPath"];

      // OpenCVクラス
      OpenCV *cv = [OpenCV alloc]; 
      // 画像ファイルを読み込み
      UIImage *src = [UIImage imageWithContentsOfFile:srcpath];

      // 画像変換
      UIImage *dst = [cv toPerspectiveTransformationImg:src];

      // JPEGに変換
      NSData *dataSaveImage = UIImageJPEGRepresentation(dst, 1);

      // ユーザーローカルに新規ファイル生成
      NSString *path = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) lastObject];
      NSString *dstpath = [path stringByAppendingPathComponent:@"temp.jpeg"];
      
      [dataSaveImage writeToFile:dstpath atomically:YES];

      // ファイルパスを返す
      result(dstpath);
    }
  }];

  return [super application:application didFinishLaunchingWithOptions:launchOptions];
}

@end
