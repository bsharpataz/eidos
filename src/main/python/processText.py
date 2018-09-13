import glob, os
import re
from collections import defaultdict
from datetime import datetime

start = "Body"
end = "Classification"
date = "Load-Date:"

def date_norm(date_str):
    try:
        normed = datetime.strptime(date_str.strip(), '%B %d, %Y')
    except:
        try :
            normed = datetime.strptime(date_str.strip(), '%b %d, %Y')
        except:
            try:
                normed = datetime.strptime(date_str.strip(), '%B %d, %Y, %A')
            except:
                try:
                    normed = datetime.strptime(date_str.strip(), '%d %b %y')
                except:
                    normed = datetime.strptime(date_str[:-2] + "0000".strip(), '%d %b %Y')

    return f'{normed.month}/{normed.day}/{normed.year}'

def article_entry(title, date_str, body):
    return {"title": title, "date": date_norm(date_str), "body": body}

# Make a map of headline to article number
def mk_file_lexicon(filename):
    file_lexicon = dict()
    articles = []
    dates = defaultdict(list)
    pattern = re.compile("^[0-9]+. (.+)")
    # filename = wdir + "/10001-10100.txt"
    print(f"file: {filename}")
    with open(filename) as f:
        lines = f.readlines()
        body = None
        body_made = False
        full_text = False
        not_stored = 0
        for i, line in enumerate(lines):
            line = line.replace("<U+2028>", "")

            # print(f"line {i}: {line.strip()}")
            # Titles
            if line.startswith("	•	 "):
                title = line.split("	•	 ")[1].strip()
                title = title.replace("<U+2028>", "")
                # print(title)
                file_lexicon[title] = len(file_lexicon)
            elif len(pattern.findall(line)) > 0:
                title = pattern.findall(line)[0]
                title = title.replace("<U+2028>", "")
                # print("!!!" + title)
                file_lexicon[title] = len(file_lexicon)
            elif line.startswith("ABSTRACT"):
                line_idx_title = i - 7
                title = lines[line_idx_title].strip()
                title = title.replace("<U+2028>", "")
                # print("ABSTRACT TITLE: " + title)
                file_lexicon[title] = len(file_lexicon)


            # Article Bodies
            if line.strip() == start:
                article_start = i + 3 # inclusive
                line_idx_title = i - 7
                article_title = lines[line_idx_title].strip()
                article_title = article_title.replace(";<U+2028>", ";")
                article_title = article_title.replace("; ", ";")
                # print("BODY TITLE:" + article_title)




                if article_title in file_lexicon:
                    article_index = file_lexicon[article_title]
                else:
                    #print(f"***** looking for title bc {article_title} not in lexicon")
                    for j in range(10):
                        article_title = lines[i - j].strip()
                        article_title = article_title.replace("<U+2028>", "")
                        for titles in file_lexicon:
                            if article_title != "" and article_title in titles:
                        # if article_title in file_lexicon:
                                article_index = file_lexicon[article_title]
                                line_idx_title = i - j
                            else:
                                continue
            if line.startswith("FULL TEXT"):
                full_text = True
                article_title = title
                #print(f"file line {i}: Title found: {article_title}")
                article_index = file_lexicon[article_title]
                article_start = i + 1
                #print(f"line 89 body_made: {body_made}")

            if line.strip() == end or line.strip().startswith("Credit: "):
                if line.strip().startswith("Credit: "):
                    if full_text == True:
                        # print(line)
                        # print(line.strip().split(" ")[-3:])
                        # art_date = line.strip().split(" ")[-3:]
                        # art_date[2] = art_date[2][:2]
                        # art_date = " ".join(art_date)
                        article_end = i - 1 # exclusive
                        if body_made != False:
                            print(f"Last body wasn't stored!\n{body} ")
                            not_stored += 1
                        body = lines[article_start:article_end]
                        body = [x.strip() for x in body]
                        body = " ".join(body)
                        body_made = True
                        #print(f"file line {i}: Article end found - setting body_made={body_made}")
                        # articles.append(article_entry(article_title, art_date, body))
                        # full_text = False
                        # body = None
                        # body_made = False
                        # print(f"file line {i}, storing body, setting body_made={body_made}")
                        #print(f"line 98 body_made: {body_made}")

                else:
                    assert body_made == False, f"Last body wasn't stored!\n{body} "
                    article_end = i - 1  # exclusive
                    body = lines[article_start:article_end]
                    body = [x.strip() for x in body]
                    body = " ".join(body)
                    body_made = True
                    # print(f"line 98 body_made: {body_made}")
                    #print(f"file line {i}: Article end found - setting body_made={body_made}")

            if line.strip() == "End of Document" and body_made == True:
                # An article was found, but not stored bc of missing date
                article_date = lines[line_idx_title + 2].strip()
                # print(f"file line {i}: article_date: {article_date}")
                article_info = article_entry(article_title, article_date, body)
                articles.append(article_info)
                full_text = False
                body = None
                body_made = False
                # print(f"file line {i}, storing body, setting body_made={body_made}")
                #print(f"line 108 body_made: {body_made}")

            if line.strip().startswith(date) or line.strip().startswith("Publication date"):
                # print(f"date found")
                # article_date = line.split(date)[1].strip()
                if line.strip().startswith(date):
                    article_date = line.split(date)[1].strip()
                else:
                    article_date = lines[i + 1].strip()
                # print(f"file line {i}: article_date: {article_date}")
                article_info = article_entry(article_title, article_date, body)
                articles.append(article_info)
                # print(f"Stored article: {body}")
                full_text = False
                body = None
                body_made = False
                # print(f"file line {i}: storing body, setting body_made={body_made}")
                #print(f"line 121 body_made: {body_made}")


    print(len(file_lexicon))
    print(len(articles))
    return articles, file_lexicon, not_stored

def main():
    directory = "3"
    wdir = f"/Users/bsharp/data/protests/0-BBC-News-Articles_Rebecca/{directory}-BBC"

    os.chdir(wdir)
    for file in glob.glob("*.txt"):
        articles, _, not_stored = mk_file_lexicon(file)
        print(f"!!!!!!! NOT Stored = {not_stored}")
        lexout = open(f"{file[:-4]}_index.txt", 'w')
        for i, article in enumerate(articles):
            # Retrieve the article info
            article_title = article["title"]
            article_body = article["body"]
            article_date = article["date"]
            # Write the index info
            lexout.write(f"{i}\t{article_title}\t{article_date}\n")
            # Write the bodies, one per file
            if article_body:
                outfile = open(f"{file[:-4]}_{i}.txt", 'w')
                outfile.write(article_body + "\n")
                outfile.close()
        lexout.close()

main()
