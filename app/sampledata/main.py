import sys
import machine
import time
import neopixel
import select

from time import sleep


class BeautifulPet:
    """
    A class to represent a beautiful pet
    This needs to be deployed to the RP2040 board
    """
    def __init__(self, name, color):
        self.led = None
        self.name = name
        self.color = color
        self.version = "0.0.1"

    def run(self):
        self.led = neopixel.NeoPixel(machine.Pin(16), 1)

        poll = select.poll()
        poll.register(sys.stdin, select.POLLIN)

        try:
            print(f"USB Serial Echo Started v.{self.version} - Send data to see it echoed back")
            while True:

                if poll.poll(0):
                    char = sys.stdin.buffer.read(1)
                    print(char)

                    if char == b'r':
                        sys.stdout.buffer.write("Turning on rainbow LED")
                        self.turn_off()
                        self.turn_rainbow_on()
                        self.turn_off()

                    # Echo it back
                    sys.stdout.buffer.write(char)

                    time.sleep(0.01)  # Small delay to prevent CPU hogging

        except KeyboardInterrupt:
            print("Program terminated by user")

    def bark(self):
        print("Woof! Woof!")

    def show(self):
        print("My name is", self.name)
        print("My color is", self.color)

    def set_color(self, color):
        self.color = color

    def get_color(self):
        return self.color

    def set_name(self, name):
        self.name = name

    def get_name(self):
        return self.name

    def turn_rainbow_on(self):
        for i in range(255):
            self.led[0] = (i, 255-i, 0)
            self.led.write()
            sleep(0.01)
        for i in range(255):
            self.led[0] = (255-i, 0, i)
            self.led.write()
            sleep(0.01)
        for i in range(255):
            self.led[0] = (0, i, 255-i)
            self.led.write()
            sleep(0.01)

    def turn_off(self):
        self.led[0] = (0, 0, 0)
        self.led.write()


if __name__ == '__main__':
    pet = BeautifulPet("Rex", "Brown")
    pet.run()