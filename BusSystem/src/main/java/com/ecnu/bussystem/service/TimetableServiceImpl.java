package com.ecnu.bussystem.service;

import com.alibaba.fastjson.JSONObject;
import com.ecnu.bussystem.entity.Station;
import com.ecnu.bussystem.entity.StationLine;
import com.ecnu.bussystem.entity.timetable.LineTimetable;
import com.ecnu.bussystem.entity.timetable.RuntimeTable;
import com.ecnu.bussystem.entity.timetable.StationTimetable;
import com.ecnu.bussystem.entity.timetable.Timetable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class TimetableServiceImpl implements TimetableService {
    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    StationServiceImpl stationService;

    @Autowired
    LineServiceImpl lineService;

    @Override
    public StationTimetable findTimetableByIdAndTime(String time, String stationId, String lineName, String count) {
        // 检查输入是否合法
        String pattern1 = "[0-9]{2}:[0-9]{2}";
        String pattern2 = "[0-9]*";
        if (time == null || stationId == null || count == null || !time.matches(pattern1) || !count.matches(pattern2)) {
            return null;
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("stationID").is(stationId));
        query.addCriteria(Criteria.where("routeName").in(lineName, lineName + "上行", lineName + "下行", lineName + "路上行", lineName + "路下行"));
        query.addCriteria(Criteria.where("passTime").gte(time));
        query.with(Sort.by(Sort.Direction.ASC, "passTime"));
        query.limit(Integer.parseInt(count));

        List<Timetable> find = mongoTemplate.find(query, Timetable.class, "timetable");
        if (find == null || find.size() == 0) {
            return null;
        }

        try {
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
            Date date = formatter.parse(time);

            // Timetable里需要增加一个字段minutes，显示几分钟后到站
            for (Timetable timetable : find) {
                String passTime = timetable.getPassTime();
                Date tmp = formatter.parse(passTime);
                int minutes = (int) (tmp.getTime() - date.getTime()) / 60 / 1000;
                timetable.setMinutes(minutes);
            }
        } catch (Exception e) {
            return null;
        }

        StationTimetable stationTimetable = new StationTimetable(find.get(0).getStationName(), stationId, find, find.size(), -1);

        return stationTimetable;
    }

    @Override
    public StationTimetable findAllTimetableByIdAndTime(String time, String stationId, String count) {
        Station station = stationService.findLineOfStationById(stationId);
        StationTimetable stationTimetable = new StationTimetable();

        if (station == null) {
            return null;
        }

        stationTimetable.setStation(station.getName());
        stationTimetable.setId(stationId);

        for (String line : station.getLines()) {
            StationTimetable tmp = findTimetableByIdAndTime(time, stationId, line, count);

            if (tmp == null || tmp.getTimetables() == null) {
                System.out.println(line + " is null");
                continue;
            }

            // 对每一条line找到count个数目
            if (stationTimetable.getTimetables() == null) {
                stationTimetable.setTimetables(tmp.getTimetables());
            }
            else {
                stationTimetable.getTimetables().addAll(tmp.getTimetables());
            }
        }

        stationTimetable.setTimetableCount(stationTimetable.getTimetables().size());

        return stationTimetable;
    }

    @Override
    public List<StationTimetable> findTimetableByNameAndTime(String time, String stationName, String lineName, String count) {
        List<StationTimetable> stationTimetables = new ArrayList<>();

        // 先在neo4j中查找所有符合name的Station
        List<Station> stationList = stationService.findStationByVagueName(stationName);

        // 找到每个Station的Timetable
        for (Station station : stationList) {
            StationTimetable stationTimetable = findTimetableByIdAndTime(time, station.getMyId(), lineName, count);
            if (stationTimetable != null && stationTimetable.isValid()) {
                stationTimetables.add(stationTimetable);
            }
        }
        return stationTimetables;
    }

    @Override
    public StationTimetable findTimetableByIdAndTimeRange(String time, int range, String stationId) {
        // 检查输入是否合法
        String pattern1 = "[0-9]{2}:[0-9]{2}";
        if (time == null || stationId == null || !time.matches(pattern1)) {
            return null;
        }

        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
        StationTimetable stationTimetable;
        try {
            Date date = formatter.parse(time);
            String time1 = formatter.format(new Date(date.getTime() + (long) range * 60 * 1000));

            Query query = new Query();
            query.addCriteria(Criteria.where("stationID").is(stationId));
            query.addCriteria(Criteria.where("passTime").gte(time).lte(time1));
            query.with(Sort.by(Sort.Direction.ASC, "passTime"));

            List<Timetable> find = mongoTemplate.find(query, Timetable.class, "timetable");
            if (find == null || find.size() == 0) {
                return null;
            }

            // Timetable里需要增加一个字段minutes，显示几分钟后到站
            for (Timetable timetable : find) {
                String passTime = timetable.getPassTime();
                Date tmp = formatter.parse(passTime);
                int minutes = (int) (tmp.getTime() - date.getTime()) / 60 / 1000;
                timetable.setMinutes(minutes);
            }

            stationTimetable = new StationTimetable( find.get(0).getStationName(), stationId, find, find.size(), -1);
        } catch (Exception e) {
            return null;
        }

        return stationTimetable;
    }

    @Override
    public List<StationTimetable> findTimetableByNameAndTimeRange(String time, int range, String stationName) {
        List<StationTimetable> stationTimetables = new ArrayList<>();

        // 先在neo4j中查找所有符合name的Station
        List<Station> stationList = stationService.findStationByVagueName(stationName);

        System.out.println(stationList.size());

        // 找到每个Station的Timetable
        for (Station station : stationList) {
            StationTimetable stationTimetable = findTimetableByIdAndTimeRange(time, range, station.getMyId());
            if (stationTimetable != null && stationTimetable.isValid()) {
                stationTimetables.add(stationTimetable);
            }
        }
        return stationTimetables;
    }

    @Override
    public LineTimetable findTimetableByName(String lineName) {
        if (lineName == null) {
            return null;
        }

        LineTimetable lineTimetable = new LineTimetable();
        List<StationTimetable> stationTimetables = new ArrayList<>();

        Query query = new Query();
        query.addCriteria(Criteria.where("routeName").is(lineName));
        query.with(Sort.by(Sort.Direction.ASC, "stationID"));
        query.with(Sort.by(Sort.Direction.ASC, "passTime"));
        List<Timetable> find = mongoTemplate.find(query, Timetable.class, "timetable");
        if (find == null || find.size() == 0) {
            return null;
        }

        // 用一个hash表记录当前处理了哪些station
        Set<String> station = new HashSet<>();
        int stationCount = 0;

        for (Timetable timetable : find) {
            String stationID = timetable.getStationID();
            String stationName = timetable.getStationName();

            // 没有这个站则进行初始化
            if (!station.contains(stationID)) {
                stationCount++;
                station.add(stationID);
                StationTimetable stationTimetable = new StationTimetable();

                stationTimetable.setStation(stationName);
                stationTimetable.setId(stationID);

                List<Timetable> timetables = new ArrayList<>();
                timetables.add(timetable);
                stationTimetable.setTimetables(timetables);

                stationTimetables.add(stationTimetable);
            }
            // 存在这个站，则从stationTimetables里取出这个站进行更新
            else {
                StationTimetable stationTimetable = stationTimetables.get(stationCount - 1);

                List<Timetable> timetables = stationTimetable.getTimetables();
                timetables.add(timetable);
                stationTimetable.setTimetables(timetables);
            }
        }

        // station之间的顺序还没有确定
        StationLine stationLine = lineService.findStationOfLineByPreciseName(lineName);
        List<Station> list = stationLine.getStations();
        if (list.size() != stationCount) {
            return null;
        }

        // 存储该station在线路上的顺序
        Map<String, Integer> stationMap = new HashMap<>();

        for (int i = 0; i < stationCount; i++) {
            stationMap.put(list.get(i).getMyId(), i);
        }

        // 存储每个station中timetable的count值和index
        for (StationTimetable stationTimetable : stationTimetables) {
            stationTimetable.setTimetableCount(stationTimetable.getTimetables().size());
            stationTimetable.setStationIndex(stationMap.get(stationTimetable.getId()));
        }

        // 根据index排序
        Collections.sort(stationTimetables);

        lineTimetable.setLine(lineName);
        lineTimetable.setStationCount(stationCount);
        lineTimetable.setTimetables(stationTimetables);

        return lineTimetable;
    }

    // 找出所有路线中运行时间最长的线路，倒序显示前15个线路
    @Override
    public List<JSONObject> findLinesOfLongestRuntime() {
        List<JSONObject> res = new ArrayList<>();
        try {
            Query query = new Query();
            query.with(Sort.by(Sort.Direction.DESC, "rate"));
            query.limit(15);
            List<RuntimeTable> find = mongoTemplate.find(query, RuntimeTable.class, "runtime");
            if (find == null || find.size() == 0) {
                System.out.println("出错啦！！");
                return null;
            }
            for (RuntimeTable tmp : find) {
                JSONObject resline = new JSONObject();
                resline.put("routeName", tmp.getName());
                resline.put("runtime", tmp.getRuntime());
                resline.put("firstBus", tmp.getFirstBus());
                resline.put("lastBus", tmp.getLastBus());
                res.add(resline);
            }
        } catch (Exception e) {
            return null;
        }
        return res;
    }
}
