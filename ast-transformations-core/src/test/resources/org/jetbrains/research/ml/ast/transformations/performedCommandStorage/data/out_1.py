def f1():
    f1_v1 = input()
    f1_v2 = 4
    f1_v3 = 5


def f2():
    f2_v1 = 5
    f2_v2 = 6
    f2_v3 = 7
    f2_v3 = ((f2_v3) + (3))
    f2_v4 = f2_v3
    f2_v2 = f2_v4
    f2_v1 = f2_v2
    f1()
    f2_v4 = f2_v4 * 4

    f2_v5 = int(input())
    if ((f2_v3) + (f2_v5)) > 14:
        print(f2_v3)
    elif ((f2_v3) + (f2_v4)) > 15:
        print(f2_v4)
    else:
        print(f2_v1)
    print(f2_v5)
    f2_v4 = f2_v4 * 4

    if (f2_v5 >= f2_v1 and f2_v4 >= f2_v5 and f2_v3 >= f2_v4):
        print(f2_v5)
    else:
        print("hello")

    if ((not f2_v1 or not f2_v2) and (not f2_v3 and not f2_v4)):
        print(f2_v5)

    if f2_v5 > f2_v1 or f2_v3 >= f2_v2:
        print(f2_v4)


if __name__ == '__main__':
    f2()
