package org.onebusaway.transit_data_federation.impl.reporting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.onebusaway.collections.tuple.T2;
import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.transit_data.model.ListBean;
import org.onebusaway.transit_data.model.StopTimeInstanceBean;
import org.onebusaway.transit_data.model.problems.StopProblemReportBean;
import org.onebusaway.transit_data.model.problems.StopProblemReportQueryBean;
import org.onebusaway.transit_data.model.problems.StopProblemReportSummaryBean;
import org.onebusaway.transit_data.model.problems.TripProblemReportBean;
import org.onebusaway.transit_data.model.problems.TripProblemReportQueryBean;
import org.onebusaway.transit_data.model.problems.TripProblemReportSummaryBean;
import org.onebusaway.transit_data_federation.services.AgencyAndIdLibrary;
import org.onebusaway.transit_data_federation.services.ArrivalAndDepartureService;
import org.onebusaway.transit_data_federation.services.beans.StopBeanService;
import org.onebusaway.transit_data_federation.services.beans.StopTimeBeanService;
import org.onebusaway.transit_data_federation.services.beans.TripBeanService;
import org.onebusaway.transit_data_federation.services.blocks.BlockInstance;
import org.onebusaway.transit_data_federation.services.blocks.BlockStatusService;
import org.onebusaway.transit_data_federation.services.realtime.ArrivalAndDepartureInstance;
import org.onebusaway.transit_data_federation.services.realtime.BlockLocation;
import org.onebusaway.transit_data_federation.services.reporting.UserReportingDao;
import org.onebusaway.transit_data_federation.services.reporting.UserReportingService;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.StopEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.onebusaway.transit_data_federation.services.tripplanner.StopTimeInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class UserReportingServiceImpl implements UserReportingService {

  private UserReportingDao _userReportingDao;

  private TransitGraphDao _graph;

  private BlockStatusService _blockStatusService;

  private ArrivalAndDepartureService _arrivalAndDepartureService;

  private TripBeanService _tripBeanService;

  private StopBeanService _stopBeanService;

  private StopTimeBeanService _stopTimeBeanService;

  @Autowired
  public void setUserReportingDao(UserReportingDao userReportingDao) {
    _userReportingDao = userReportingDao;
  }

  @Autowired
  public void setGrah(TransitGraphDao graph) {
    _graph = graph;
  }

  @Autowired
  public void setBlockStatusService(BlockStatusService blockStatusService) {
    _blockStatusService = blockStatusService;
  }

  @Autowired
  public void setArrivalAndDepartureService(
      ArrivalAndDepartureService arrivalAndDepartureService) {
    _arrivalAndDepartureService = arrivalAndDepartureService;
  }

  @Autowired
  public void setTripBeanService(TripBeanService tripBeanService) {
    _tripBeanService = tripBeanService;
  }

  @Autowired
  public void setStopBeanService(StopBeanService stopBeanService) {
    _stopBeanService = stopBeanService;
  }

  @Autowired
  public void setStopTimeBeanService(StopTimeBeanService stopTimeBeanService) {
    _stopTimeBeanService = stopTimeBeanService;
  }

  @Override
  public void reportProblemWithStop(StopProblemReportBean problem) {

    StopProblemReportRecord record = new StopProblemReportRecord();

    String stopId = problem.getStopId();
    if (stopId != null)
      record.setStopId(AgencyAndIdLibrary.convertFromString(stopId));

    record.setTime(problem.getTime());
    record.setData(problem.getData());
    record.setUserComment(problem.getUserComment());

    if (!Double.isNaN(problem.getUserLat()))
      record.setUserLat(problem.getUserLat());
    if (!Double.isNaN(problem.getUserLon()))
      record.setUserLon(problem.getUserLon());
    if (!Double.isNaN(problem.getUserLocationAccuracy()))
      record.setUserLocationAccuracy(problem.getUserLocationAccuracy());

    record.setStatus(problem.getStatus());

    _userReportingDao.saveOrUpdate(record);
  }

  @Override
  public void reportProblemWithTrip(TripProblemReportBean problem) {

    AgencyAndId tripId = AgencyAndIdLibrary.convertFromString(problem.getTripId());

    TripEntry trip = _graph.getTripEntryForId(tripId);

    if (trip == null)
      return;

    BlockEntry block = trip.getBlock();

    TripProblemReportRecord record = new TripProblemReportRecord();
    record.setData(problem.getData());
    record.setServiceDate(problem.getServiceDate());

    String vehicleId = problem.getVehicleId();
    if (vehicleId != null)
      record.setVehicleId(AgencyAndIdLibrary.convertFromString(vehicleId));

    String stopId = problem.getStopId();
    if (stopId != null)
      record.setStopId(AgencyAndIdLibrary.convertFromString(stopId));

    record.setTime(problem.getTime());
    record.setTripId(tripId);
    record.setBlockId(block.getId());

    record.setUserComment(problem.getUserComment());

    if (!Double.isNaN(problem.getUserLat()))
      record.setUserLat(problem.getUserLat());
    if (!Double.isNaN(problem.getUserLon()))
      record.setUserLon(problem.getUserLon());
    if (!Double.isNaN(problem.getUserLocationAccuracy()))
      record.setUserLocationAccuracy(problem.getUserLocationAccuracy());

    record.setUserOnVehicle(problem.isUserOnVehicle());
    record.setUserVehicleNumber(problem.getUserVehicleNumber());

    Map<BlockInstance, List<BlockLocation>> locationsByInstance = _blockStatusService.getBlocks(
        block.getId(), problem.getServiceDate(), record.getVehicleId(),
        problem.getTime());

    BlockInstance blockInstance = getBestBlockInstance(locationsByInstance.keySet());

    if (blockInstance != null) {

      List<BlockLocation> blockLocations = locationsByInstance.get(blockInstance);

      BlockLocation blockLocation = getBestLocation(blockLocations, problem);

      if (blockLocation != null) {

        record.setPredicted(blockLocation.isPredicted());

        if (blockLocation.isDistanceAlongBlockSet())
          record.setDistanceAlongBlock(blockLocation.getDistanceAlongBlock());

        if (blockLocation.isScheduleDeviationSet())
          record.setScheduleDeviation(blockLocation.getScheduleDeviation());

        CoordinatePoint p = blockLocation.getLocation();
        if (p != null) {
          record.setVehicleLat(p.getLat());
          record.setVehicleLon(p.getLon());
        }
        record.setMatchedVehicleId(blockLocation.getVehicleId());
      }
    }

    record.setStatus(problem.getStatus());

    _userReportingDao.saveOrUpdate(record);
  }

  @Override
  public ListBean<StopProblemReportSummaryBean> getStopProblemReportSummaries(
      StopProblemReportQueryBean query) {

    List<T2<AgencyAndId, Integer>> records = _userReportingDao.getStopProblemReportSummaries(
        query.getAgencyId(), query.getTimeFrom(), query.getTimeTo(),
        query.getStatus());

    List<StopProblemReportSummaryBean> beans = new ArrayList<StopProblemReportSummaryBean>(
        records.size());

    for (T2<AgencyAndId, Integer> record : records) {
      AgencyAndId stopId = record.getFirst();
      Integer count = record.getSecond();
      StopProblemReportSummaryBean bean = new StopProblemReportSummaryBean();
      bean.setStop(_stopBeanService.getStopForId(stopId));
      bean.setStatus(query.getStatus());
      bean.setCount(count);
      beans.add(bean);
    }

    return new ListBean<StopProblemReportSummaryBean>(beans, false);
  }

  @Override
  public ListBean<TripProblemReportSummaryBean> getTripProblemReportSummaries(
      TripProblemReportQueryBean query) {

    List<T2<AgencyAndId, Integer>> records = _userReportingDao.getTripProblemReportSummaries(
        query.getAgencyId(), query.getTimeFrom(), query.getTimeTo(),
        query.getStatus());

    List<TripProblemReportSummaryBean> beans = new ArrayList<TripProblemReportSummaryBean>(
        records.size());

    for (T2<AgencyAndId, Integer> record : records) {
      AgencyAndId tripId = record.getFirst();
      Integer count = record.getSecond();
      TripProblemReportSummaryBean bean = new TripProblemReportSummaryBean();
      bean.setTrip(_tripBeanService.getTripForId(tripId));
      bean.setStatus(query.getStatus());
      bean.setCount(count);
      beans.add(bean);
    }

    return new ListBean<TripProblemReportSummaryBean>(beans, false);
  }

  public ListBean<StopProblemReportBean> getStopProblemReports(
      StopProblemReportQueryBean query) {
    List<StopProblemReportRecord> records = _userReportingDao.getStopProblemReports(
        query.getAgencyId(), query.getTimeFrom(), query.getTimeTo(),
        query.getStatus());
    List<StopProblemReportBean> beans = new ArrayList<StopProblemReportBean>(
        records.size());
    for (StopProblemReportRecord record : records)
      beans.add(getRecordAsBean(record));
    return new ListBean<StopProblemReportBean>(beans, false);
  }

  public ListBean<TripProblemReportBean> getTripProblemReports(
      TripProblemReportQueryBean query) {
    List<TripProblemReportRecord> records = _userReportingDao.getTripProblemReports(
        query.getAgencyId(), query.getTimeFrom(), query.getTimeTo(),
        query.getStatus());
    List<TripProblemReportBean> beans = new ArrayList<TripProblemReportBean>(
        records.size());
    for (TripProblemReportRecord record : records)
      beans.add(getRecordAsBean(record));
    return new ListBean<TripProblemReportBean>(beans, false);
  }

  @Override
  public List<StopProblemReportBean> getAllStopProblemReportsForStopId(
      AgencyAndId stopId) {
    List<StopProblemReportRecord> records = _userReportingDao.getAllStopProblemReportsForStopId(stopId);
    List<StopProblemReportBean> beans = new ArrayList<StopProblemReportBean>(
        records.size());
    for (StopProblemReportRecord record : records)
      beans.add(getRecordAsBean(record));
    return beans;
  }

  @Override
  public List<TripProblemReportBean> getAllTripProblemReportsForTripId(
      AgencyAndId tripId) {
    List<TripProblemReportRecord> records = _userReportingDao.getAllTripProblemReportsForTripId(tripId);
    List<TripProblemReportBean> beans = new ArrayList<TripProblemReportBean>(
        records.size());
    for (TripProblemReportRecord record : records)
      beans.add(getRecordAsBean(record));
    return beans;
  }

  @Override
  public StopProblemReportBean getStopProblemReportForId(long id) {
    StopProblemReportRecord record = _userReportingDao.getStopProblemRecordForId(id);
    return getRecordAsBean(record);
  }

  @Override
  public TripProblemReportBean getTripProblemReportForId(long id) {
    TripProblemReportRecord record = _userReportingDao.getTripProblemRecordForId(id);
    return getRecordAsBean(record);
  }

  @Override
  public void deleteStopProblemReportForId(long id) {
    StopProblemReportRecord record = _userReportingDao.getStopProblemRecordForId(id);
    if (record != null)
      _userReportingDao.delete(record);
  }

  @Override
  public void updateTripProblemReport(TripProblemReportBean bean) {
    TripProblemReportRecord record = _userReportingDao.getTripProblemRecordForId(bean.getId());
    if (record == null)
      return;
    record.setStatus(bean.getStatus());
    record.setLabel(bean.getLabel());
    _userReportingDao.saveOrUpdate(record);
  }

  @Override
  public void deleteTripProblemReportForId(long id) {
    TripProblemReportRecord record = _userReportingDao.getTripProblemRecordForId(id);
    if (record != null)
      _userReportingDao.delete(record);
  }

  @Override
  public List<String> getAllTripProblemReportLabels() {
    return _userReportingDao.getAllTripProblemReportLabels();
  }

  /****
   * Private Methods
   ****/

  private BlockInstance getBestBlockInstance(
      Collection<BlockInstance> blockInstances) {

    if (blockInstances.isEmpty())
      return null;

    // Could we do something better here?
    return blockInstances.iterator().next();
  }

  private BlockLocation getBestLocation(List<BlockLocation> blockLocations,
      TripProblemReportBean problem) {

    if (blockLocations.isEmpty())
      return null;

    // Could we do something better here?
    return blockLocations.get(0);
  }

  private StopProblemReportBean getRecordAsBean(StopProblemReportRecord record) {

    AgencyAndId stopId = record.getStopId();

    StopProblemReportBean bean = new StopProblemReportBean();
    bean.setData(record.getData());
    bean.setId(record.getId());
    bean.setStatus(record.getStatus());
    bean.setStopId(AgencyAndIdLibrary.convertToString(stopId));
    bean.setTime(record.getTime());
    bean.setUserComment(record.getUserComment());

    if (record.getUserLat() != null)
      bean.setUserLat(record.getUserLat());
    if (record.getUserLon() != null)
      bean.setUserLon(record.getUserLon());
    if (record.getUserLocationAccuracy() != null)
      bean.setUserLocationAccuracy(record.getUserLocationAccuracy());

    if (stopId != null)
      bean.setStop(_stopBeanService.getStopForId(stopId));

    return bean;
  }

  private TripProblemReportBean getRecordAsBean(TripProblemReportRecord record) {

    AgencyAndId stopId = record.getStopId();
    AgencyAndId tripId = record.getTripId();

    TripProblemReportBean bean = new TripProblemReportBean();

    bean.setData(record.getData());
    bean.setId(record.getId());
    bean.setServiceDate(record.getServiceDate());
    bean.setStatus(record.getStatus());
    bean.setLabel(record.getLabel());
    bean.setStopId(AgencyAndIdLibrary.convertToString(stopId));
    bean.setTime(record.getTime());
    bean.setTripId(AgencyAndIdLibrary.convertToString(tripId));
    bean.setUserComment(record.getUserComment());

    bean.setUserLat(record.getUserLat());
    bean.setUserLon(record.getUserLon());
    bean.setUserLocationAccuracy(record.getUserLocationAccuracy());

    bean.setUserOnVehicle(record.isUserOnVehicle());
    bean.setUserVehicleNumber(record.getUserVehicleNumber());

    bean.setPredicted(record.isPredicted());

    bean.setDistanceAlongBlock(record.getDistanceAlongBlock());
    bean.setScheduleDeviation(record.getScheduleDeviation());
    bean.setVehicleLat(record.getVehicleLat());
    bean.setVehicleLon(record.getVehicleLon());

    if (stopId != null)
      bean.setStop(_stopBeanService.getStopForId(stopId));
    if (tripId != null)
      bean.setTrip(_tripBeanService.getTripForId(tripId));

    if (tripId != null && stopId != null) {
      TripEntry trip = _graph.getTripEntryForId(tripId);
      StopEntry stop = _graph.getStopEntryForId(stopId);
      if (trip != null && stop != null) {

        AgencyAndId vehicleId = record.getMatchedVehicleId();
        if (vehicleId == null)
          vehicleId = record.getVehicleId();

        ArrivalAndDepartureInstance instance = _arrivalAndDepartureService.getArrivalAndDepartureForStop(
            stop, -1, trip, record.getServiceDate(), vehicleId,
            record.getTime());

        if (instance != null) {
          StopTimeInstance sti = new StopTimeInstance(
              instance.getBlockStopTime(), instance.getServiceDate());
          StopTimeInstanceBean stopTimeBean = _stopTimeBeanService.getStopTimeInstanceAsBean(sti);
          bean.setStopTime(stopTimeBean);
        }
      }
    }

    return bean;
  }
}
