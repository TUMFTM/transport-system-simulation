# This script calculates the performance indicators from a simulation-output-database.
# Pass the input parameters when running the script from command-line. 
# No configuration/alternation of script required.

from datetime import datetime
from datetime import timedelta
import argparse
import sqlite3
import pandas as pd
import numpy as np
import ntpath
import math
from scipy import stats as st
import pyperclip


class ARGS:
    INPUT_FILE = ""
    STARTTIME = ""
    ENDTIME = ""


def main():
    args = getArguments()
    ARGS.INPUT_FILE = args.input
    ARGS.STARTTIME = args.starttime
    ARGS.ENDTIME = args.endtime

    # Connect to database
    conn = sqlite3.connect(ARGS.INPUT_FILE)

    # Get info about trip status
    query = 'select * from log_trips'
    trips_data = pd.read_sql_query(query, conn)
    trips_data['orig_start_time'] = pd.to_datetime(trips_data['orig_start_time'])

    trips_data_completed = trips_data.loc[(trips_data['status'] == 'COMPLETED') & (trips_data['orig_distance_km'] > 0.1)].copy()

    trips_data_completed['orig_start_time'] = pd.to_datetime(trips_data_completed['orig_start_time'])
    #trips_data_completed['orig_stop_time'] = pd.to_datetime(trips_data_completed['orig_stop_time'])
    trips_data_completed['time_trip_picked_up'] = pd.to_datetime(
        trips_data_completed['time_trip_picked_up'])
    trips_data_completed['time_trip_drop_off'] = pd.to_datetime(
        trips_data_completed['time_trip_drop_off'])
    trips_data_completed['time_trip_departure'] = pd.to_datetime(
        trips_data_completed['time_trip_departure'])
    trips_data_completed['time_trip_completed'] = pd.to_datetime(
        trips_data_completed['time_trip_completed'])

    if (ARGS.STARTTIME == ""):
        start_time = trips_data_completed['orig_start_time'].min().replace(minute=0,    second=0)
    else:
        start_time = ARGS.STARTTIME
    
    if (ARGS.ENDTIME == ""):
        end_time = (trips_data_completed['orig_start_time'].max() + timedelta(hours=1)).replace(minute=0,    second=0)
    else:
        end_time = ARGS.ENDTIME

    # Filter the data
    if ARGS.STARTTIME != "" or ARGS.ENDTIME != "":
        trips_data = trips_data.loc[(trips_data['orig_start_time'] >= start_time) & (trips_data['orig_start_time'] <= end_time)].copy()
        trips_data_completed = trips_data_completed.loc[(trips_data_completed['orig_start_time'] >= start_time) & (trips_data_completed['orig_start_time'] <= end_time)].copy()

    # Calculate original stop time
    trips_data_completed['orig_stop_time'] = trips_data_completed.orig_start_time + (trips_data_completed.factored_duration_min*60 * np.timedelta64(1, 's'))

    service_rate = trips_data_completed.shape[0] / trips_data.shape[0]

    # Waiting Time
    mean_waiting_time = np.mean(
        trips_data_completed.time_trip_picked_up - trips_data_completed.orig_start_time).total_seconds()
    median_waiting_time = np.median(
        trips_data_completed.time_trip_picked_up - trips_data_completed.orig_start_time) / np.timedelta64(1, 's')

    # Trip Elongation
    mean_trip_duration_elongation = np.mean(
        trips_data_completed.driving_duration - trips_data_completed.factored_duration_min) * 60
    mean_trip_duration_elongation_percentage = np.mean(trips_data_completed.driving_duration / trips_data_completed.factored_duration_min)-1
    median_trip_duration_elongation = np.median(
        trips_data_completed.driving_duration - trips_data_completed.factored_duration_min) * 60
    median_trip_duration_elongation_percentage = np.median(
        trips_data_completed.driving_duration / trips_data_completed.factored_duration_min) - 1

    # Total Trip Delay
    mean_total_trip_duration_elongation = np.mean(trips_data_completed.time_trip_completed - trips_data_completed.orig_stop_time).total_seconds()
    mean_total_trip_duration_elongation_percentage = np.mean((((trips_data_completed.time_trip_completed - trips_data_completed.orig_start_time)/ np.timedelta64(1, 's'))/(trips_data_completed.factored_duration_min*60))-1)
    median_total_trip_duration_elongation = np.median(trips_data_completed.time_trip_completed - trips_data_completed.orig_stop_time)/ np.timedelta64(1, 's')
    median_total_trip_duration_elongation_percentage = np.median(((trips_data_completed.time_trip_completed - trips_data_completed.orig_start_time)/ np.timedelta64(1, 's'))/(trips_data_completed.factored_duration_min*60)-1)

    # Dwell Time
    mean_dwell_time = np.mean(((trips_data_completed.time_trip_completed - trips_data_completed.time_trip_picked_up)/ np.timedelta64(1, 's'))-trips_data_completed.driving_duration*60)

    # Trip Distance Elongation
    mean_trip_distance_elongation = np.mean(
        trips_data_completed.driving_distance - trips_data_completed.orig_distance_km)
    median_trip_distance_elongation = np.median(
        trips_data_completed.driving_distance - trips_data_completed.orig_distance_km)
    mean_trip_distance_elongation_percentage = np.mean(trips_data_completed.driving_distance / trips_data_completed.orig_distance_km)-1
    median_trip_distance_elongation_percentage = np.median(trips_data_completed.driving_distance / trips_data_completed.orig_distance_km)-1


    shared_trips = trips_data_completed.loc[trips_data_completed['trip_was_shared'] == 1].shape[0]
    shared_trip_rate = shared_trips / trips_data_completed.shape[0]

    # DATA ABOUT PAX COUNT
    query = "SELECT pax_count, status FROM log_simobject_status WHERE object_type LIKE '%Vehicle'"
    vehicle_pax_data = pd.read_sql_query(query, conn)

    mean_pax_count_all = np.mean(vehicle_pax_data.pax_count)
    mean_pax_count_busy = np.mean(vehicle_pax_data.loc[vehicle_pax_data.status == 'VEHICLE_BUSY'].pax_count)

    # DATA ABOUT VEHILCE DRIVING DISTANCE
    query = "SELECT * FROM log_stats_vehicle"
    vehicle_stats_data = pd.read_sql_query(query, conn)

    mean_veh_driving_distance = np.mean(vehicle_stats_data.driving_distance_km)
    median_veh_driving_distance = np.median(vehicle_stats_data.driving_distance_km)

    mean_served_requests = np.mean(vehicle_stats_data.served_requests)
    median_served_requests = np.median(vehicle_stats_data.served_requests)
    mean_served_passengers = np.mean(vehicle_stats_data.served_passengers)
    median_served_passengers = np.median(vehicle_stats_data.served_passengers)

    mean_max_idle_duration = np.mean(vehicle_stats_data.dur_idle_max_min)
    median_max_idle_duration = np.median(vehicle_stats_data.dur_idle_max_min)

    sum_energy_consumption = np.sum(vehicle_stats_data.energy_consumption_kwh)
    sum_duration_idle = np.sum(vehicle_stats_data.dur_idle_sum_min)
    sum_duration_busy_driving = np.sum(vehicle_stats_data.dur_busy_drive_sum_min)
    sum_duration_busy_dwell = np.sum(vehicle_stats_data.dur_busy_dwell_sum_min)
    sum_duration_relocating = np.sum(vehicle_stats_data.dur_relocation_sum_min)

    max_pax_on_vehicle = max(vehicle_stats_data.max_simultaneous_pax)


    # DATA ABOUT FLEET DRIVING DISTANCE
    query = "SELECT pax_count, request_count, step_type, distance_km FROM log_routes WHERE object_type LIKE '%Vehicle' AND step_type LIKE 'ENROUTE%'"
    log_route_data = pd.read_sql_query(query, conn)

    if log_route_data.shape[0] > 0:
        #avg_pax_count_ENRT = np.mean(log_route_data.loc[(log_route_data.step_type=='ENROUTE')].pax_count)
        #avg_pax_count_ENRT_without_empty = np.mean(log_route_data.loc[(log_route_data.step_type=='ENROUTE') & (log_route_data.pax_count>0)].pax_count)

        log_route_data['weighted_paxcount'] = log_route_data.pax_count * log_route_data.distance_km
        b=log_route_data.loc[(log_route_data.step_type=='ENROUTE') & (log_route_data.pax_count>0)]
        avg_pax_count_dist_weighted = sum(b.weighted_paxcount)/sum(b.distance_km)
        
        total_vmt = sum(log_route_data.distance_km)
        vmt_ENRT = sum(log_route_data.loc[(log_route_data.step_type=='ENROUTE')].distance_km)
        vmt_ENRT_gt0 = sum(log_route_data.loc[(log_route_data.step_type=='ENROUTE') & (log_route_data.pax_count>0)].distance_km)
        vmt_ENRT_sh = sum(log_route_data.loc[(log_route_data.step_type=='ENROUTE') & (log_route_data.request_count>1)].distance_km)
        vmt_ENRT_eq0 = sum(log_route_data.loc[(log_route_data.step_type=='ENROUTE') & (log_route_data.pax_count==0)].distance_km)
        vmt_RELOC = sum(log_route_data.loc[(log_route_data.step_type=='ENROUTE_RELOCATION')].distance_km)

        trip_original_vmt = sum(trips_data_completed.orig_distance_km)
        trip_vmt_ratio = vmt_ENRT_gt0/trip_original_vmt -1
        trip_vmt_total_ratio = total_vmt/trip_original_vmt -1
        trip_vmt_enrt_shared_ratio = vmt_ENRT_sh/vmt_ENRT_gt0
    else:
        avg_pax_count_ENRT = 0
        avg_pax_count_dist_weighted = 0
        total_vmt = 1
        vmt_ENRT = 0
        vmt_ENRT_gt0 = 0
        vmt_ENRT_sh = 0
        vmt_ENRT_eq0 = 0
        vmt_RELOC = 0

        trip_original_vmt = sum(trips_data_completed.orig_distance_km)
        trip_vmt_ratio = 0
        trip_vmt_total_ratio = 0
        trip_vmt_enrt_shared_ratio = 0

    # DATA ABOUT SIMULATION DURATION
    query = "select value as duration, strftime('%s','1970-01-01 '|| value) as dur_sec from log_configuration where key like 'Simulation%'"
    cur = conn.cursor()
    cur.execute(query)
    data = cur.fetchall()
    sim_duration_string = data[0][0]
    sim_duration_seconds = data[0][1]

    if sim_duration_seconds is None:
        hour, min, sec = sim_duration_string.split(":")
        sim_duration_seconds = int(hour)*60*60 + int(min)*60 + int(sec)

    all_data = [service_rate, shared_trip_rate, mean_waiting_time, mean_trip_duration_elongation, mean_pax_count_all,
                mean_veh_driving_distance, mean_pax_count_busy, avg_pax_count_dist_weighted,
                total_vmt, vmt_ENRT, vmt_ENRT_gt0, vmt_ENRT_eq0, vmt_RELOC, max_pax_on_vehicle]

    # values = ""
    # for elem in all_data:
    #     values = values + "{}\n".format(elem)
    #     # print(elem)
    #
    # values = values.replace('.', ',')
    # pyperclip.copy(values)

    # DB Properties
    # print("{:25}: {}".format("DB File", ARGS.INPUT_FILE))

    output_string = "{:25}: {:0.1f}%\n".format("Service Rate", service_rate*100) + \
                    "{:25}: {:0.1f}%\n".format("Shared Rate", shared_trip_rate*100) + \
                    "{:25}: {:0.2f} s\n".format("Avg. waiting time", mean_waiting_time) + \
                    "{:25}: {:0.2f} s\n".format("Med. waiting time", median_waiting_time) + \
                    "{:25}: {:0.2f} s\n".format("Avg. dwell time", mean_dwell_time) + \
                    "{:25}: {:0.2f} s\n".format("Avg. dur elongation", mean_trip_duration_elongation) + \
                    "{:25}: {:0.2f} %\n".format("Avg. dur elongation %", mean_trip_duration_elongation_percentage*100) + \
                    "{:25}: {:0.2f} s\n".format("Med. dur elongation", median_trip_duration_elongation) + \
                    "{:25}: {:0.2f} %\n".format("Med. dur elongation %",
                                                median_trip_duration_elongation_percentage * 100) + \
                    "{:25}: {:0.2f} s\n".format("Avg. total dur elong.", mean_total_trip_duration_elongation) + \
                    "{:25}: {:0.2f} %\n".format("Avg. total dur elong. %",
                                                mean_total_trip_duration_elongation_percentage * 100) + \
                    "{:25}: {:0.2f} s\n".format("Med. total dur elong.", median_total_trip_duration_elongation) + \
                    "{:25}: {:0.2f} %\n".format("Med. total dur elong. %",
                                                median_total_trip_duration_elongation_percentage * 100) + \
                    "{:25}: {:0.2f} km\n".format("Avg. dist elongation", mean_trip_distance_elongation) + \
                    "{:25}: {:0.2f} %\n".format("Avg. dist elongation %",
                                                mean_trip_distance_elongation_percentage * 100) + \
                    "{:25}: {:0.2f} km\n".format("Med. dist elongation", median_trip_distance_elongation) + \
                    "{:25}: {:0.2f} %\n".format("Med. dist elongation %",
                                                median_trip_distance_elongation_percentage * 100) + \
                    "{:25}: {:0.2f}\n".format("Alsono mean pax count", mean_pax_count_all) + \
                    "{:25}: {:0.2f} km\n".format("Mean vehicle distance", mean_veh_driving_distance) + \
                    "{:25}: {:0.2f} km\n".format("Median vehicle distance", median_veh_driving_distance) + \
                    "{:25}: {:0.2f}\n".format("Avg. Pax Count BUSY time weighted", mean_pax_count_busy) + \
                    "{:25}: {:0.2f}\n".format("Avg. Pax Count ENRT>0Pax dist weighted", avg_pax_count_dist_weighted) + \
                    "{:25}: {:0.2f} km\n".format("TOTAL VMT", total_vmt) + \
                    "{:25}: {:0.2f} km ({:0.1f}%)\n".format("VMT ENRTE", vmt_ENRT, vmt_ENRT/total_vmt*100) + \
                    "{:25}: {:0.2f} km ({:0.1f}%)\n".format("VMT ENRTE > 0 Pax", vmt_ENRT_gt0, vmt_ENRT_gt0/total_vmt*100) + \
                    "{:25}: {:0.2f} km ({:0.1f}%)\n".format("VMT ENRTE > 1 Pax", vmt_ENRT_sh, vmt_ENRT_sh/total_vmt*100) + \
                    "{:25}: {:0.2f} km ({:0.1f}%)\n".format("VMT ENRTE = 0 Pax", vmt_ENRT_eq0, vmt_ENRT_eq0/total_vmt*100) + \
                    "{:25}: {:0.2f} km ({:0.1f}%)\n".format("VMT RELOC", vmt_RELOC, vmt_RELOC/total_vmt*100) + \
                    "{:25}: {:0.2f} km\n".format("VMT ORIG", trip_original_vmt) + \
                    "{:25}: {:0.2f} %\n".format("VMT ENRTE>0/VMT ORIG", trip_vmt_ratio*100) + \
                    "{:25}: {:0.2f} %\n".format("VMT SH/VMT ENRTE>0", trip_vmt_enrt_shared_ratio*100) + \
                    "{:25}: {:0.2f} %\n".format("TOTAL VMT/VMT ORIG", trip_vmt_total_ratio*100) + \
                    "{:25}: {:0.2f}\n".format("Mean served requests", mean_served_requests) + \
                    "{:25}: {:0.2f}\n".format("Median served requests", median_served_requests) + \
                    "{:25}: {:0.2f}\n".format("Mean served passengers", mean_served_passengers) + \
                    "{:25}: {:0.2f}\n".format("Median served passengers", median_served_passengers) + \
                    "{:25}: {:0.2f} kwh\n".format("Fleet energy consumption", sum_energy_consumption) + \
                    "{:25}: {:0.2f} min\n".format("Mean idle dur", mean_max_idle_duration) + \
                    "{:25}: {:0.2f} min\n".format("Median idle dur", median_max_idle_duration) + \
                    "{:25}: {:0.0f} min\n".format("Total veh idle dur", sum_duration_idle) + \
                    "{:25}: {:0.0f} min\n".format("Total veh busy drv dur", sum_duration_busy_driving) + \
                    "{:25}: {:0.0f} min\n".format("Total veh busy dwl dur", sum_duration_busy_dwell) + \
                    "{:25}: {:0.0f} min\n".format("Total veh busy reloc dur", sum_duration_relocating) + \
                    "{:25}: {}\n".format("Sim duration", sim_duration_string) + \
                    "{:25}: {:0.2f} s\n".format("Sim duration sec", int(sim_duration_seconds)) + \
                    "{:25}: {:0.0f}\n".format("Max pax on vehicle", int(max_pax_on_vehicle))


    # Print results to console and to file
    print('\nDB STATS: ' + ARGS.INPUT_FILE + '\n' + output_string)
    # with open(ntpath.basename(ARGS.INPUT_FILE) + ".txt", 'w') as f:
    with open(ARGS.INPUT_FILE + ".txt", 'w') as f:
        print(output_string, file=f)

    vl_string = "{}\n".format(service_rate) + \
                "{}\n".format(shared_trip_rate) + \
                "{}\n".format(mean_waiting_time) + \
                "{}\n".format(median_waiting_time) + \
                "{}\n".format(mean_dwell_time) + \
                "{}\n".format(mean_trip_duration_elongation) + \
                "{}\n".format(mean_trip_duration_elongation_percentage) + \
                "{}\n".format(median_trip_duration_elongation) + \
                "{}\n".format(median_trip_duration_elongation_percentage) + \
                "{}\n".format(mean_total_trip_duration_elongation) + \
                "{}\n".format(mean_total_trip_duration_elongation_percentage) + \
                "{}\n".format(median_total_trip_duration_elongation) + \
                "{}\n".format(median_total_trip_duration_elongation_percentage) + \
                "{}\n".format(mean_trip_distance_elongation) + \
                "{}\n".format(mean_trip_distance_elongation_percentage) + \
                "{}\n".format(median_trip_distance_elongation) + \
                "{}\n".format(median_trip_distance_elongation_percentage) + \
                "{}\n".format(mean_pax_count_all) + \
                "{}\n".format(mean_veh_driving_distance) + \
                "{}\n".format(median_veh_driving_distance) + \
                "{}\n".format(mean_pax_count_busy) + \
                "{}\n".format(avg_pax_count_dist_weighted) + \
                "{}\n".format(total_vmt) + \
                "{}\n".format(vmt_ENRT) + \
                "{}\n".format(vmt_ENRT_gt0) + \
                "{}\n".format(vmt_ENRT_sh) + \
                "{}\n".format(vmt_ENRT_eq0) + \
                "{}\n".format(vmt_RELOC) + \
                "{}\n".format(trip_original_vmt) + \
                "{}\n".format(trip_vmt_ratio) + \
                "{}\n".format(trip_vmt_enrt_shared_ratio) + \
                "{}\n".format(trip_vmt_total_ratio) + \
                "{}\n".format(mean_served_requests) + \
                "{}\n".format(median_served_requests) + \
                "{}\n".format(mean_served_passengers) + \
                "{}\n".format(median_served_passengers) + \
                "{}\n".format(sum_energy_consumption) + \
                "{}\n".format(mean_max_idle_duration) + \
                "{}\n".format(median_max_idle_duration) + \
                "{}\n".format(sum_duration_idle) + \
                "{}\n".format(sum_duration_busy_driving) + \
                "{}\n".format(sum_duration_busy_dwell) + \
                "{}\n".format(sum_duration_relocating) + \
                "{}\n".format(sim_duration_string) + \
                "{}\n".format(sim_duration_seconds) + \
                "{}\n".format(max_pax_on_vehicle)

    vl_string=vl_string.replace('.',',')

    # with open(ntpath.basename(ARGS.INPUT_FILE) + ".csv", 'w') as f:
    with open(ARGS.INPUT_FILE + ".csv", 'w') as f:
        print(vl_string, file=f)

    # print("mean_pax_count_busy: {:0.2f}".format(mean_pax_count_busy))


def getArguments():
    parser = argparse.ArgumentParser()
    parser.add_argument('input', type=str, help='Path to db with results.')
    parser.add_argument('--starttime',    '-st',    type=str,    help='Starttime (format: \'yyyy-mm-dd hh:mm:ss\')',    default="")
    parser.add_argument('--endtime',    '-et',    type=str,    help='Endtime (format: \'yyyy-mm-dd hh:mm:ss\')',    default="")
    return parser.parse_args()


if __name__ == '__main__':
    main()
