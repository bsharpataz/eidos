from collections import defaultdict, namedtuple
import csv
import os
import glob
import re
import string

Extraction = namedtuple('Extraction', 'file_prefix article_index sentence_id aid eid news_id title date event_type actor theme sentence_text')
Master = namedtuple("Master", 'RA news_id published_date title aid eid countryname')
Index = namedtuple("Index", 'file_prefix article_index title date')
MiniMaster = namedtuple("MiniMaster", 'news_id aid eid date')

def aggregate(extraction, master_info, index_info):
    print(extraction)


# Index files:
# bsharp@Beckys-MacBook-Pro ~/data/protests/0-BBC-News-Articles_Rebecca/1-BBC/indices]$ head 1-100_index.txt
# 0	GORBACHEV's MEETINGS WITH LEADERS OF TWO LITHUANIAN COMMUNIST PARTIES	1/8/1990
# 1	PROCEEDINGS	1/10/1990
# 2	SAJUDIS RALLY IN VILNIUS ON 11TH JANUARY	1/13/1990
# 3	IN BRIEF;Television workers protest at losing jobs	1/22/1990

def read_index(filename):
    index_info = []
    with open(filename) as f:
        for line in f:
            file_prefix = filename.split("/")[-1][:-10]
            #print(file_prefix)
            fields = line.rstrip().split("\t")
            #if len(fields != )
            index_info.append(Index(file_prefix, *fields)) # unpack fields
    return index_info



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


def fuzzy_dates(date):
    fds = []
    m, d, y = date.split("/")
    for i in range(1, 3):
        fds.append(f"{m}/{int(d) + i}/{y}")
        fds.append(f"{m}/{int(d) - i}/{y}")
    return fds

def backoff_lookup(title, date, master_backoff):
    m = None
    if sanitize(title) in master_backoff:
        ms = master_backoff[(sanitize(title))]
        if date in master_backoff: # unlikely!
            m = ms[date]
        else:
            fdworked = False
            for fd in fuzzy_dates(date):
                if fd in ms:
                    fdworked = True
                    m = ms[fd]
            if fdworked == False:
                print(f"***FD didn't work with: {date} {title}")
    else:
        m = None
    return m

def read_extraction_file(fn, master_info, master_backoff, all_index_info):
    extractions = []
    none_ctr = 0
    backoff_ctr = 0
    empty_title_ctr = 0
    file_prefix, file_index = get_file_prefix_and_index(fn)
    # get the title and date from the index
    title, date = all_index_info[file_prefix][file_index]
    # print(f'title: {title}, date: {date}')
    # get the aid, eid, newsid
    if title != "":
        if date in master_info:
            if title in master_info[date]:
                # normal
                m = master_info[date][title]
            else:
                # backoff
                print(f'fn: {fn}, title: {title}, date: {date}')
                m = backoff_lookup(title, date, master_backoff)
                # if sanitize(title) in master_backoff:
                #     m = master_backoff[sanitize(title)]
                #     backoff_ctr += 1
                # else:
                #     m = None

        else:
            # backoff
            m = backoff_lookup(title, date, master_backoff)
            # if sanitize(title) in master_backoff:
            #     m = master_backoff[sanitize(title)]
            #     backoff_ctr += 1
            # else:
            #     m = None
    else:
        m = None
        empty_title_ctr += 1


    # if master_info[date] == {}:
    #     print(f'title: {title}, date: {date}')
    #     print(master_info[date])
    #     print()

    if m:

        with open(fn) as f:
            for line in f:
                line = line.rstrip()
                if line != "file	sentence_id	aid	eid	news_id	title	date	event_type	actor	theme	sentence_text":
                    _, sentence_id, _, _, _, _, _, event_type, actor, theme, sentence_text = line.split("\t")

                    extracted_info = Extraction(file_prefix, file_index, sentence_id, m.aid, m.eid, m.news_id, title, date, event_type, actor, theme, sentence_text)
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
        header = 'file_prefix article_index sentence_id aid eid news_id title date event_type actor theme sentence_text'.split(" ")
        csv_writer = csv.writer(csv_file, delimiter='\t')
        csv_writer.writerow(header)
        for e in extractions:
            csv_writer.writerow(list(e))




### ------------------------------------------------------------
###                             UTILS
### ------------------------------------------------------------

def onlyascii(char):
    if ord(char) < 48 or ord(char) > 127: return ''
    else: return char

def sanitize2(s):
    s2 = ''.join([onlyascii(c) for c in s])
    return s2

def sanitize(s):
    s = s.strip()
    if s.endswith("."):
        s = s[:-1]
    # for whitespace_character in string.whitespace:
    #     s.replace(whitespace_character, '')
    s = re.sub('"', '', s)
    s = re.sub('"', '', s)
    s = re.sub(" ", "", s)
    s.replace(' ', '')
    s.replace('-CorrectionAppended', '')
    s = re.sub("&quot;", "", s)
    s = ''.join(s.splitlines())
    return s

def get_file_prefix(index_filename):
    return index_filename[:-10]

def get_file_prefix_and_index(extraction_filename):
    return (extraction_filename[:-14].split("_"))

def convert_index_to_dict(all_index_info):
    #Index = namedtuple("Index", 'file_prefix article_index title date')
    out = defaultdict(dict)
    for index_info in all_index_info:
        out[index_info.file_prefix][index_info.article_index] = [index_info.title, index_info.date]
    return out

def convert_master_to_dict(master_info):
    # Master = namedtuple("Master", 'RA news_id published_date title aid eid countryname')
    # MiniMaster = namedtuple("MiniMaster", 'news_id aid eid')
    out = dict()
    for master in master_info:
        print(master)
        out[master.aid] = master
    return out

def get_files(path, ext):
    os.chdir(path)
    return glob.glob(f"*{ext}")

# def main():
#
#     # # load the master index info
#     master_file = "/Users/bsharp/data/protests/0-BBC-News-Articles_Rebecca/0_NEW_ArticleLIST_Master_1-10679.csv"
#     master_info = read_master_csv(master_file)
#     master_aid_dict = convert_master_to_dict(master_info)
#
#     # final resting place...
#     extractions = []
#
#     none_ctr = 0
#     backoff_ctr = 0
#     num_files = 0
#
#     for directory in ["1", "2", "3"]:
#
#         # load the index info
#         # indices_dir = f"/Users/bsharp/data/protests/0-BBC-News-Articles_Rebecca/{directory}-BBC/indices"
#         # filelist = get_files(indices_dir, "txt")
#         # all_index_info = []
#         # for f in filelist:
#         #     all_index_info.extend(read_index(f))
#         # all_index_info = convert_index_to_dict(all_index_info)
#
#
#         # get the list of extraction files
#         extraction_dir = f"/Users/bsharp/data/protests/0-BBC-News-Articles_Rebecca/{directory}-BBC/extractions"
#         filelist = get_files(extraction_dir, "tsv")
#         for f in filelist:
#             num_files += 1
#             file_extractions, none_ctr_file, backoff_ctr_file = read_extraction_file(f, master_info, master_info_backoff, all_index_info)
#             extractions.extend(file_extractions)
#             none_ctr += none_ctr_file
#             backoff_ctr += backoff_ctr_file


    # # export as one thing
    # print(f"There were a total of {none_ctr} files that were unusable bc of index issues.")
    # # print(f"There were a total of {backoff_ctr} files that were found with the backoff.")
    # print(f"There were a total of {backoff_ctr} files that were unused bc of empty titles.")
    # print(f"There were a total of {num_files} files.")
    # outputfile = "/Users/bsharp/data/protests/0-BBC-News-Articles_Rebecca/extractions_all_coverage.tsv"
    # # export_extractions(extractions, outputfile)
    # #



# main()