package com.example.mygcs2;

import android.graphics.Color;

import com.naver.maps.map.NaverMap;

public class Values {
    public static int TMP_DISTANCE =20;
    public static double TMP_ANGLE = 90;
    public static NaverMap.MapType MAPTYPE_DEFAULT = NaverMap.MapType.Basic;

    public static int POLYGON_OUTLINE_WIDTH = 5;
    public static int POLYGON_OUTLINE_COLOR = Color.BLUE;
    public static int POLYGON_COLOR = Color.TRANSPARENT;

    public static int START_POINT_NEAREST = 0;
    public static int START_POINT_LONGEST = 1;

    public static double EARTH = 6378.137;  //radius of the earth in kilometer

    public static int DIAGONAL_TYPE_HORIZONTAL = 0;
    public static int DIAGONAL_TYPE_VERTICAL = 1;
    public static int DIAGONAL_TYPE_NW_TO_SE = 2;
    public static int DIAGONAL_TYPE_NE_TO_SW = 3;

    public static int DIRECTION_NE =0;
    public static int DIRECTION_NW =1;
    public static int DIRECTION_SE =2;
    public static int DIRECTION_SW =3;


}
