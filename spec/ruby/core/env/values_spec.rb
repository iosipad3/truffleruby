require_relative '../../spec_helper'

describe "ENV.values" do

  it "returns an array of the values" do
    ENV.values.should == ENV.to_hash.values
  end

  it "uses the locale encoding" do
    ENV.values.each do |value|
      value.encoding.should == Encoding.find('locale')
    end
  end
end
