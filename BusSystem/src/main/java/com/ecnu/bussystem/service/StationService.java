package com.ecnu.bussystem.service;

import com.ecnu.bussystem.entity.Station;

import java.util.List;
import java.util.Map;

public interface StationService {
    // 根据站点id查找站点信息
    Station findStationById(String Id);

    // 根据站点name查找站点信息
    List<Station> findStationByPreciseName(String stationName);

    // 根据站点的模糊name查找站点信息（所有的首发和终点站）
    List<Station> findStationByVagueName(String stationName);

    // 根据站点模糊name查找经过站点的所有线路
    List<Station> findLineOfStationByVagueName(String stationName);

    //根据id分组返回线路最多的15个站点
    List<Map<String,Object>> findTop15LineNumberofStations();

    //查询地铁站的数量
    List<String> findNumberOfMetroStations();
}
