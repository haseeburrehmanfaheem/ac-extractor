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




directory_name = "../outputMIX2S/prob-rules" 
api_file_name = directory_name + "/apis_with_ids.csv"



def find_files_with_prefix(directory, prefix):
    content_array = []
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.startswith(prefix):
                with open(os.path.join(root, file), 'r') as f:
                    content_array.extend(f.readlines())
    return content_array


def main(args):
    if(len(args) != 2):
        print(str(args))
        print("Usage: python3 finder.py <directory_path_probrules> <outputcsvpath.csv>")
        exit(1)
    directory_name = args[0] 
    api_file_name = directory_name + "/apis_with_ids.csv"
    outputpath = args[1]
    content_array = find_files_with_prefix(directory_name, "output")
    cleaned_content = [line.replace("\n", "").replace("\t", "").strip() for line in content_array]
    extracted_data = []
    for line in cleaned_content:
        match = re.search(r"fapi_ac\(rapi(\d+),(\w+)\):([0-9.e-]+)", line)
        if match:
            rapi_number, system_x2, value = match.groups()
            if value != "0":
                extracted_data.append(("api" + rapi_number, system_x2, value))
    highest_values = {}
    for number, category, value in extracted_data:
        value = float(value)  # Convert the value to float for comparison
        if number not in highest_values or value > highest_values[number][1]:
            highest_values[number] = (category, value)

    inferenceDf = pd.DataFrame([(number, cat, val) for number, (cat, val) in highest_values.items()],
                  columns=['api', 'protection', 'Probability'])

    # Write to CSV
    csv_filename = './inference.csv'
    inferenceDf.to_csv(csv_filename, index=False)

    print(f"Data written to {csv_filename}")
    column_names = [i for i in range(0, 3)]
    originalApiDf = pd.read_csv(api_file_name, header=None, usecols=column_names)



    rename_dict = {
        0: 'api',
        2: 'protection',
    }

    originalApiDf.rename(columns=rename_dict, inplace=True)

    originalApiDf['protection'] = originalApiDf['protection'].replace({
        "NONE": "no_ac",
        "SYS_OR_SIG" : "system",
        "NORMAL" : "normal",
    })
    merged_df = pd.merge(originalApiDf, inferenceDf, on=['api'], suffixes=('_actual', '_infer'))
    mismatches = merged_df[merged_df['protection_actual'] != merged_df['protection_infer']]

    # mismatches

    # mismatches.drop(columns=['protection_actual', 'Probability'], inplace=True)

    mismatches.to_csv(outputpath, index=False)
    print(f"final output exported to {outputpath}")
    













if __name__ == "__main__":
    main(sys.argv[1:])
