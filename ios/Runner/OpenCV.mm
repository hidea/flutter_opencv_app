//
//  OpenCV.m
//  Runner
//
//  Created by HIDEAKI YAMANA on 2019/09/10.
//  Copyright © 2019 The Chromium Authors. All rights reserved.
//

#import <opencv2/opencv.hpp>
#import <opencv2/imgcodecs/ios.h>

#import "OpenCV.h"

@implementation OpenCV

- (UIImage *) toPerspectiveTransformationImg:(UIImage *)img{

    // UIImageをMatに変換
    cv::Mat matSource;
    UIImageToMat(img, matSource);

    // 前処理

    // グレースケール変換
    cv::cvtColor(matSource, matSource, cv::COLOR_BGR2GRAY);
    // ぼかしをいれる
    cv::blur(matSource, matSource, cv::Size(5.0, 5.0));
    // 2値化
    cv::adaptiveThreshold(matSource, matSource, 255.0, cv::ADAPTIVE_THRESH_MEAN_C, cv::THRESH_BINARY, 21, 16);
    // Cannyアルゴリズムを使ったエッジ検出
    cv::Mat matCanny;
    cv::Canny(matSource, matCanny, 75, 200);
    // 膨張
    cv::Mat matKernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(9.0, 9.0));
    cv::dilate(matCanny, matCanny, matKernel);

    // 輪郭を取得

    std::vector< std::vector<cv::Point> > vctContours;
    std::vector< cv::Vec4i > hierarchy;
    cv::findContours(matCanny, vctContours, hierarchy, cv::RETR_LIST, cv::CHAIN_APPROX_SIMPLE);

    // 面積順にソート
    std::sort(vctContours.begin(), vctContours.end(), [](const std::vector<cv::Point>& c1, const std::vector<cv::Point>& c2){
        return cv::contourArea(c1, false) > cv::contourArea(c2, false);
    });

    // 最大の四角形を走査、変換元の矩形にする
    std::vector<cv::Point2f> ptSrc;    
    for (int i=0; i<vctContours.size(); ++i) {
        // 輪郭をまるめる
        std::vector<cv::Point> approxCurve;
        double arclen = cv::arcLength(vctContours[i], true);
        cv::approxPolyDP(vctContours[i], approxCurve, 0.02 * arclen, true);

        // 4辺の矩形なら採用
        if (approxCurve.size() == 4) {
            for (int j=0; j<4; ++j) {
                ptSrc.push_back(cv::Point2f(approxCurve[j].x, approxCurve[j].y));
            }
            break;
        }
    }
    if (ptSrc.empty()) {
        return nil;
    }

    // 変換先の矩形（元画像の幅を最大にした名刺比率にする）
    float width = img.size.width;
    float height = width / 1.654;
    std::vector<cv::Point2f> ptDst;
    ptDst.push_back(cv::Point2f(0, 0));
    ptDst.push_back(cv::Point2f(0, height));
    ptDst.push_back(cv::Point2f(width, height));
    ptDst.push_back(cv::Point2f(width, 0));
    
    // 変換行列
    cv::Mat matTrans = cv::getPerspectiveTransform(ptSrc, ptDst);

    // 変換
    cv::Mat matResult(width, height, matSource.type());
    cv::warpPerspective(matSource, matResult, matTrans, cv::Size(width, height));

    // MatをUIImageに変換する
    UIImage *resultImg = MatToUIImage(matResult);

    return resultImg;
}

@end
