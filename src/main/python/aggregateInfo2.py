from collections import defaultdict, namedtuple
import csv
import os, sys
import glob
import re
import string

Extraction = namedtuple('Extraction', 'file_prefix article_index sentence_id aid eid news_id title date event_type hedge_neg actor actor_number actor_location theme them_actor theme_grounding theme_location sentence_locations all_locations sentence_text rule')
Master = namedtuple("Master", 'RA news_id published_date title aid eid countryname')


def read_aid_index(filename):
    aid_index = dict()
    with open(filename) as f:
        for line in f:
            ext_filename, aid, eid = line.strip().split("\t")
            aid_index[ext_filename[:-4]] = aid_key(aid, eid)
    print(aid_index)
    # sys.exit()
    return aid_index


# RA,news_id,published date (swb_pubdate),title,CHECK  (Saved in folder),"CHECK (Saved in docx)",MEMO  (any concern),aid,eid,year,month,day,countryname
def read_master_csv(filename):
    master_info = []
    with open(filename) as csv_file:
        csv_reader = csv.reader(csv_file, delimiter=',')
        line_count = 0
        for row in csv_reader:
            if line_count == 0:
                print(f'Column names are {", ".join(row)}')
                line_count += 1
            else:
                RA, news_id, published_date, title, _, _, _, aid, eid, _, _, _, countryname = row
                master_info.append(Master(RA, news_id, published_date, title, aid, eid, countryname))
                line_count += 1
        print(f'Processed {line_count} lines.')
    return master_info

def aid_key(aid, eid):
    return f"{aid}_{eid}"

def read_extraction_file(fn, master_aid_dict, aid_index):
    extractions = []
    none_ctr = 0
    empty_title_ctr = 0

    file_prefix, file_index = get_file_prefix_and_index(fn)

    file_key = f"{file_prefix}_{file_index}"

    if file_key in aid_index:
        k = aid_index[file_key]
        m = master_aid_dict[k]
    else:
        print(f"not in aidindex: {file_key} (fn: {fn})")
        m = None

    if m:

        with open(fn) as f:
            for line in f:
                line = line.strip()
                if not line.startswith("file"):
                    #print(line)
                    _, sentence_id, _, _, _, _, _, event_type, hedge_neg, actor, actor_number, actor_location, theme, theme_actor, theme_grounding, theme_location, sentence_locations, all_locations, sentence_text, rule_name = line.split("\t")

                    extracted_info = Extraction(file_prefix, file_index, sentence_id, m.aid, m.eid, m.news_id, m.title, m.published_date, event_type, hedge_neg, actor, actor_number, actor_location, theme, theme_actor, theme_grounding, theme_location, sentence_locations, all_locations, sentence_text, rule_name)
                    # extracted_info.title = title
                    # extracted_info.date = date
                    #print(extracted_info)
                    extractions.append(extracted_info)
    else:
        none_ctr += 1

    # if none_ctr > 0:
    #     print(f"none: {none_ctr}")
    return extractions, none_ctr, empty_title_ctr


def export_extractions(extractions, fn):
    with open(fn, 'w') as csv_file:
        header = 'file_prefix article_index sentence_id aid eid news_id title date event_type hedge_neg actor actor_number actor_location theme theme_actor theme_grounding theme_location sentence_locations all_locations sentence_text rule_name'.split(" ")
        csv_writer = csv.writer(csv_file, delimiter='\t')
        csv_writer.writerow(header)
        for e in extractions:
            if e.sentence_id == "sentence_id":
                print("YIKES!")
            csv_writer.writerow(list(e))




### ------------------------------------------------------------
###                             UTILS
### ------------------------------------------------------------



def get_file_prefix_and_index(extraction_filename):
    return (extraction_filename[:-14].split("_"))


def convert_master_to_dict(master_info):
    # Master = namedtuple("Master", 'RA news_id published_date title aid eid countryname')
    # MiniMaster = namedtuple("MiniMaster", 'news_id aid eid')
    out = dict()
    for master in master_info:
        print(master)
        k = aid_key(master.aid, master.eid)
        out[k] = master
    return out

def get_files(path, ext):
    os.chdir(path)
    return glob.glob(f"*{ext}")

def main():

    # # load the master index info
    master_file = "/Users/bsharp/data/protests/0-BBC-News-Articles_Rebecca/0_NEW_ArticleLIST_Master_1-10679.csv"
    master_info = read_master_csv(master_file)
    master_aid_dict = convert_master_to_dict(master_info)

    aid_index = read_aid_index("/Users/bsharp/data/protests/aids.tsv")

    # final resting place...
    extractions = []

    none_ctr = 0
    backoff_ctr = 0
    num_files = 0

    # get the list of extraction files
    # extraction_dir = f"/Users/bsharp/data/protests/all_files/extractions"
    # extraction_dir = f"/Users/bsharp/data/protests/with_per/extractions"
    # extraction_dir = f"/Users/bsharp/data/protests/oct13/extractions"
    extraction_dir = f"/Users/bsharp/data/protests/dev_out"
    filelist = get_files(extraction_dir, "tsv")
    for f in filelist:
        num_files += 1
        file_extractions, none_ctr_file, backoff_ctr_file = read_extraction_file(f, master_aid_dict, aid_index)
        extractions.extend(file_extractions)
        none_ctr += none_ctr_file
        backoff_ctr += backoff_ctr_file


    # export as one thing
    print(f"There were a total of {none_ctr} files that were unusable bc of index issues.")
    # print(f"There were a total of {backoff_ctr} files that were found with the backoff.")
    print(f"There were a total of {backoff_ctr} files that were unused bc of empty titles.")
    print(f"There were a total of {num_files} files.")
    # outputfile = "/Users/bsharp/data/protests/extractions_all_oct13.tsv"
    outputfile = "/Users/bsharp/data/protests/extractions_dev.tsv"
    export_extractions(extractions, outputfile)
    #



main()