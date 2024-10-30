import sys
import torch
from sklearn.metrics.pairwise import cosine_similarity
from unixcoder import UniXcoder

#model for Unixcoder
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
MODEL = UniXcoder("microsoft/unixcoder-base")
MODEL.to(DEVICE)

# Calculates similarity value between two sentences - unixcoder
def simCalculator(sentence1, sentence2):
    sentence1_embeddings = generateEmbeddingForSentence(sentence1)
    sentence2_embeddings = generateEmbeddingForSentence(sentence2)

    return cosine_similarity(sentence1_embeddings.detach(), sentence2_embeddings.detach())[0][0]

# Generates normalized embeddings for a given classname -unixcoder
def generateEmbeddingForSentence(sentence):
    global MODEL, DEVICE

    tokens_ids = MODEL.tokenize([sentence],max_length=512,mode="<encoder-only>")
    source_ids = torch.tensor(tokens_ids).to(DEVICE)
    _, sentence_embedding = MODEL(source_ids)
    normal_sentence_embedding = torch.nn.functional.normalize(sentence_embedding, p=2, dim=1)

    return normal_sentence_embedding

if len(sys.argv) != 3:
    print("Usage: python3 similarity_detector <code_seg_1> <code_seg_2>")
    sys.exit(2)

print(simCalculator(str(sys.argv[1]), str(sys.argv[2])))
