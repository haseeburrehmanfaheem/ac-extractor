import os
import re
from problog.program import PrologString
from problog import get_evaluatable
import csv
import pandas as pd 
import signal
import time
import multiprocessing
import os
import glob
import re
import sys

def check_second_entry_exists(row, ref_df):
    return row.iloc[1] in ref_df.iloc[:, 1].values



def process_files_with_prefix(directory, prefix, api_numbers):
    api_numbers_set = set(api_numbers)
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.startswith(prefix):
                found_numbers = set()
                with open(os.path.join(root, file), 'r') as f:
                    for line in f:
                        for number in api_numbers_set:
                            if number in line:
                                found_numbers.add(number)
                new_file_path = os.path.join(root, f"processed_{file}")
                with open(new_file_path, 'w') as out_file:
                    if(len(found_numbers) > 0):
                        print(f"Found {len(found_numbers)} numbers in folder {root}")
                    for number in found_numbers:
                        out_file.write(number + '\n')


def main(args):
    if(len(args) != 2):
        print("Usage: python3 removeAOSP.py <directory_path_output> <AOSPapi.csv>")
        exit(1)
    
    directory_name = args[0]
    api_file_name = directory_name + "/prob-rules/apis_with_ids.csv"
    aospAPIs = args[1]
    
    try:
        aospAPI = pd.read_csv(aospAPIs)
        customApis = pd.read_csv(directory_name+"/Entrypoints.csv")
    except:
        print("Error reading files, check path!")
        print("custom path = ", directory_name+"/Entrypoints.csv")
        print("Aosp path = " + aospAPIs)
        exit(1)
    
    with open('./apis_custom.txt', 'w') as file:
        for index, row in customApis.iterrows():
            if not check_second_entry_exists(row, aospAPI):
                file.write(str(row.iloc[1]) + '\n')

    print("custom apis written to apis_custom.txt")

    customapi = './apis_custom.txt'
    customApis = []
    with open(customapi, 'r') as file:
        for line in file:
            customApis.append(line.strip())  
    customApisset = set(customApis)
    
    
    allApis = pd.read_csv(api_file_name, usecols=[0,1], header=None)
    matching_apis = []
    for index, row in allApis.iterrows():
        # print(row[1])
        if row[1] in customApisset:
            matching_apis.append(row[0])
            
            
    process_files_with_prefix(directory_name, "api", matching_apis)

if __name__ == "__main__":
    main(sys.argv[1:])