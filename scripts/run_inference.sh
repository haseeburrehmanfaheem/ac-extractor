infer_api() {
    RULE_FILE=`echo "${0}" | cut -f1 -d' '`
    TIMEOUT=`echo "${0}" | cut -f2 -d' '`
    OUTFILE=`echo "${0}" | cut -f3 -d' '`
    problog -t "${TIMEOUT}" "${RULE_FILE}" > "${OUTFILE}"
    cat "${OUTFILE}"
    #echo "${0}"
}

export -f infer_api

if [ $# != 5 ] ; then
    echo "Usage: ./run_inference.sh <prob-rules-folder-path> <prob-header-path> <timeout-in-sec> <num-parallel-proc> <api-list-file-name>"
    echo "Aborting $0."
    exit 1
fi
for filename in "${1}"/*/; do
    [ -e "$filename" ] || continue
    OBS="${filename}observations"
    APIS="${filename}${5}"
    echo "Processing ${filename}"
    rm -f "${filename}output.txt"
    ALL_RULES=$(<"${2}")
    ALL_RULES+="
"
    ALL_RULES+=$(<"${OBS}")
    ALL_CMD=()
    while IFS= read -r line; do
        APIID="${line//[$'\t\r\n ']}"
        NEW_RULES="${ALL_RULES}
"
    	NEW_RULES+="query(fapi_ac("
    	NEW_RULES+="r${line//[$'\t\r\n ']}"
    	NEW_RULES+=",_))."
    	echo "$NEW_RULES" > "${filename}all_rules_${APIID}.pl"
    	ALL_CMD+=("${filename}all_rules_${APIID}.pl ${3} ${filename}output_${APIID}.txt")
    done < "${APIS}"
    printf '%s\n' "${ALL_CMD[@]}" | xargs --max-procs=${4} -I CMD bash -c infer_api "CMD"
done
