import pandas as pd
import sys

def compare_csvs(csv1_path, csv2_path, output_path):
    csv1 = pd.read_csv(csv1_path)
    csv2 = pd.read_csv(csv2_path)
    ## change the name of the columns here
    etColumn = 'Entrypoint'
    levelColumn = 'Access Control Level'
    required_columns = [etColumn,levelColumn]
    try:
        merged = pd.merge(csv1, csv2, on=etColumn, suffixes=('_csv1', '_csv2'))
    except:
        print("Error merging the two csv files(change column maybe)")
        return
    differing_entrypoints = merged[merged[levelColumn+ '_csv1'] != merged[levelColumn+'_csv2']]
    differing_entrypoints[etColumn].to_csv(output_path, index=False)
    print("Differing EntryPoints have been written to " + output_path)

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python compareApi.py <csv1_path> <csv2_path> <output_path>")
        sys.exit(1)

    csv1_path = sys.argv[1]
    csv2_path = sys.argv[2]
    output_path = sys.argv[3]
    compare_csvs(csv1_path, csv2_path, output_path)
