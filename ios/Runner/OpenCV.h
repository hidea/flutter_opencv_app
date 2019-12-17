//
//  OpenCV.h
//  Runner
//
//  Created by HIDEAKI YAMANA on 2019/09/10.
//  Copyright © 2019 The Chromium Authors. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface OpenCV : NSObject

- (UIImage *)toPerspectiveTransformationImg:(UIImage *)img;

@end

NS_ASSUME_NONNULL_END
