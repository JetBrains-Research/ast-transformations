
def f1():
    f1_v1 = 4
    f1_v2 = 5


def f2():
    f2_v1 = 5
    f2_v2 = 6
    f2_v3 = 7
    f2_v3 = f2_v3 + 3
    f2_v4 = f2_v3
    f2_v2 = f2_v4
    f2_v1 = f2_v2
    f1()
    f2_v4 = f2_v4 * (2 + 2)

    f2_v5 = int(input())

    if (f2_v1 <= f2_v5 and f2_v5 <= f2_v4 and f2_v4 <= f2_v3):
        print(f2_v5)
    else:
        print("hello")

if __name__ == '__main__':
    f2()